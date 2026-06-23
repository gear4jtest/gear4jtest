package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.repository.jdbc.ExternalRepositorySqlDialect;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;

public final class DatabaseArtifactStore implements ArtifactStore {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_]\\w{0,63}");
    private static final String DEFAULT_TABLE = "artifact_store";

    private final DataSource ds;
    private final String table;
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;

    public static Builder builder() {
        return new Builder();
    }

    private DatabaseArtifactStore(Builder builder) {
        this.ds = Objects.requireNonNull(builder.dataSource, "ds must not be null");
        this.table = requireSqlIdentifier(builder.table == null ? DEFAULT_TABLE : builder.table, "table name");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
    }

    public static final class Builder {
        private DataSource dataSource;
        private String table = DEFAULT_TABLE;
        private Gear4jDatabaseDialect databaseDialect;
        private JdbcStatementOptions statementOptions = JdbcStatementOptions.defaults();

        private Builder() {
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        public Builder databaseDialect(Gear4jDatabaseDialect databaseDialect) {
            this.databaseDialect = databaseDialect;
            return this;
        }

        public Builder jdbcStatementTimeout(Duration jdbcStatementTimeout) {
            this.statementOptions = JdbcStatementOptions.of(jdbcStatementTimeout);
            return this;
        }

        public Builder statementOptions(JdbcStatementOptions statementOptions) {
            this.statementOptions = statementOptions;
            return this;
        }

        public DatabaseArtifactStore build() {
            return new DatabaseArtifactStore(this);
        }
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }

    private static String requireSqlIdentifier(String value, String label) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    @Override
    public String put(byte[] content) throws IOException {
        Objects.requireNonNull(content, "content must not be null");
        return put(new ByteArrayInputStream(content), content.length);
    }

    @Override
    public String put(InputStream in, long maxBytes) throws IOException {
        Objects.requireNonNull(in, "input stream must not be null");
        Path tmp = Files.createTempFile("gear4j-artifact-", ".bin");
        try {
            long size = spoolToTempFile(in, tmp, maxBytes);
            String hash;
            try (InputStream digestInput = Files.newInputStream(tmp)) {
                hash = Hashing.sha256Hex(digestInput, ArtifactStore.UNLIMITED_SIZE).hashHex();
            }
            try (var c = ds.getConnection();
                    var ps = prepare(c, "INSERT INTO " + table
                            + "(hash_hex,size_bytes,content) VALUES (?,?,?)")) {
                ps.setString(1, hash);
                ps.setLong(2, size);
                try (InputStream content = Files.newInputStream(tmp)) {
                    ps.setBinaryStream(3, content, size);
                    try {
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        if (!ExternalRepositorySqlDialect.isUniqueViolation(databaseDialect, e)) {
                            throw e;
                        }
                    }
                }
                return hash;
            } catch (SQLException e) {
                throw new IOException("DB error", e);
            }
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
                // Best-effort cleanup: the operation result must not be masked by temp-file
                // deletion failure.
            }
        }
    }

    private static long spoolToTempFile(InputStream in, Path target, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (var out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (maxBytes >= 0 && total > maxBytes) {
                    throw new IOException("Artifact size exceeds configured limit. maxBytes=" + maxBytes);
                }
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        String hash = Hashing.requireSha256Hex(hashHex);
        try (var c = ds.getConnection();
                var ps = prepare(c, "SELECT size_bytes, content FROM " + table + " WHERE hash_hex=?")) {
            ps.setString(1, hash);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long size = rs.getLong(1);
                byte[] bytes = rs.getBytes(2);
                byte[] snapshot = bytes == null ? new byte[0] : bytes.clone();
                return Optional.of(new Artifact(hash, size, Map.of(), () -> new ByteArrayInputStream(snapshot)));
            }
        } catch (SQLException e) {
            throw new IOException("DB error", e);
        }
    }

    @Override
    public boolean exists(String hashHex) throws IOException {
        String hash = Hashing.requireSha256Hex(hashHex);
        try (var c = ds.getConnection(); var ps = prepare(c, "SELECT 1 FROM " + table + " WHERE hash_hex=?")) {
            ps.setString(1, hash);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IOException("DB error", e);
        }
    }
}
