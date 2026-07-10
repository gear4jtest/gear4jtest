package io.github.gear4jtest.external.jdbc.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.artifact.ArtifactSpoolMonitor;
import io.github.gear4jtest.external.api.artifact.ArtifactSpoolPolicy;
import io.github.gear4jtest.external.api.artifact.ArtifactSpoolStats;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.ArtifactStoreMonitor;
import io.github.gear4jtest.external.api.artifact.ArtifactStoreStats;
import io.github.gear4jtest.external.api.artifact.ManagedArtifactSpool;
import io.github.gear4jtest.external.jdbc.repository.ExternalRepositorySqlDialect;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;

public final class DatabaseArtifactStore implements ArtifactStore, ArtifactStoreMonitor, ArtifactSpoolMonitor {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_]\\w{0,63}");
    private static final String DEFAULT_TABLE = "artifact_store";

    private final DataSource ds;
    private final String table;
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;
    private final long maxArtifactSizeBytes;
    private final ManagedArtifactSpool spool;
    private final ArtifactStoreMetrics metrics = new ArtifactStoreMetrics();

    public static Builder builder() {
        return new Builder();
    }

    private DatabaseArtifactStore(Builder builder) {
        this.ds = Objects.requireNonNull(builder.dataSource, "ds must not be null");
        this.table = requireSqlIdentifier(builder.table == null ? DEFAULT_TABLE : builder.table, "table name");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
        this.maxArtifactSizeBytes = validateMaxArtifactSize(builder.maxArtifactSizeBytes);
        try {
            this.spool = new ManagedArtifactSpool(builder.spoolPolicy);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to initialize the private artifact spool", exception);
        }
    }

    public static final class Builder {
        private DataSource dataSource;
        private String table = DEFAULT_TABLE;
        private Gear4jDatabaseDialect databaseDialect;
        private JdbcStatementOptions statementOptions = JdbcStatementOptions.defaults();
        private long maxArtifactSizeBytes = ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES;
        private ArtifactSpoolPolicy spoolPolicy = ArtifactSpoolPolicy.defaults();

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

        public Builder maxArtifactSizeBytes(long maxArtifactSizeBytes) {
            this.maxArtifactSizeBytes = maxArtifactSizeBytes;
            return this;
        }

        public Builder spoolDirectory(Path spoolDirectory) {
            this.spoolPolicy = spoolPolicy.toBuilder().directory(spoolDirectory).build();
            return this;
        }

        public Builder spoolPolicy(ArtifactSpoolPolicy spoolPolicy) {
            this.spoolPolicy = Objects.requireNonNull(spoolPolicy, "spoolPolicy must not be null");
            return this;
        }

        public Builder spoolMaxBytes(long spoolMaxBytes) {
            this.spoolPolicy = spoolPolicy.toBuilder().maxBytes(spoolMaxBytes).build();
            return this;
        }

        public Builder spoolStaleFileAge(Duration spoolStaleFileAge) {
            this.spoolPolicy = spoolPolicy.toBuilder().staleFileAge(spoolStaleFileAge).build();
            return this;
        }

        public DatabaseArtifactStore build() {
            return new DatabaseArtifactStore(this);
        }
    }

    @Override
    public ArtifactStoreStats snapshotStats() {
        return metrics.snapshot();
    }

    @Override
    public ArtifactSpoolStats snapshotSpoolStats() {
        return spool.snapshotStats();
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
        try {
            requireAllowedSize(content.length, maxArtifactSizeBytes);
        } catch (IOException exception) {
            metrics.recordWriteFailure(0L);
            throw exception;
        }
        return put(new ByteArrayInputStream(content), content.length);
    }

    @Override
    public String put(InputStream in, long maxBytes) throws IOException {
        Objects.requireNonNull(in, "input stream must not be null");
        long effectiveMaxBytes = minimumLimit(maxArtifactSizeBytes, validateMaxArtifactSize(maxBytes));
        long startedNanos = System.nanoTime();
        Path tempFile = null;
        try {
            tempFile = spool.createTempFile("database-");
            long size = spoolToTempFile(in, tempFile, effectiveMaxBytes);
            String hash;
            try (InputStream digestInput = Files.newInputStream(tempFile)) {
                hash = ArtifactHashes.sha256Hex(digestInput, size).hashHex();
            }
            writeArtifact(tempFile, hash, size);
            metrics.recordWriteCompleted(size, ArtifactStoreMetrics.elapsedSince(startedNanos));
            return hash;
        } catch (IOException | RuntimeException exception) {
            metrics.recordWriteFailure(ArtifactStoreMetrics.elapsedSince(startedNanos));
            throw exception;
        } finally {
            spool.delete(tempFile);
        }
    }

    private void writeArtifact(Path tempFile, String hash, long size) throws IOException {
        try (Connection connection = ds.getConnection();
                PreparedStatement statement = prepare(connection, "INSERT INTO " + table
                        + "(hash_hex,size_bytes,content) VALUES (?,?,?)")) {
            statement.setString(1, hash);
            statement.setLong(2, size);
            try (InputStream content = Files.newInputStream(tempFile)) {
                statement.setBinaryStream(3, content, size);
                try {
                    int insertedRows = statement.executeUpdate();
                    if (insertedRows != 1) {
                        throw new SQLException("Expected to insert one artifact but inserted " + insertedRows);
                    }
                } catch (SQLException exception) {
                    if (!ExternalRepositorySqlDialect.isUniqueViolation(databaseDialect, exception)) {
                        throw exception;
                    }
                    verifyExistingArtifactSize(connection, hash, size);
                }
            }
        } catch (SQLException exception) {
            throw new IOException("Failed to persist database artifact " + hash + " using " + databaseDialect,
                    exception);
        }
    }

    private void verifyExistingArtifactSize(Connection connection, String hash, long expectedSize) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "SELECT size_bytes FROM " + table + " WHERE hash_hex=?")) {
            statement.setString(1, hash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Artifact unique conflict was reported but no row exists for " + hash);
                }
                long actualSize = resultSet.getLong(1);
                if (actualSize != expectedSize) {
                    throw new SQLException("Existing artifact size mismatch for " + hash + ": expected "
                            + expectedSize + " but found " + actualSize);
                }
            }
        }
    }

    private long spoolToTempFile(InputStream in, Path target, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (var out = spool.openOutput(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                requireAllowedSize(total, maxBytes);
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
        long startedNanos = System.nanoTime();
        try (Connection connection = ds.getConnection();
                PreparedStatement statement = prepare(connection,
                                                      "SELECT size_bytes FROM " + table + " WHERE hash_hex=?")) {
            statement.setString(1, hash);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                long size = resultSet.getLong(1);
                requireStoredSize(hash, size);
                return Optional.of(Artifact.streaming(hash, size, Map.of(), () -> openContentStream(hash, size)));
            }
        } catch (SQLException exception) {
            IOException failure = new IOException(
                    "Failed to find database artifact " + hash + " using " + databaseDialect, exception);
            metrics.recordReadOpenFailure(ArtifactStoreMetrics.elapsedSince(startedNanos));
            throw failure;
        } catch (IOException | RuntimeException exception) {
            metrics.recordReadOpenFailure(ArtifactStoreMetrics.elapsedSince(startedNanos));
            throw exception;
        }
    }

    private InputStream openContentStream(String hash, long expectedSize) throws IOException {
        long startedNanos = System.nanoTime();
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        InputStream content = null;
        try {
            connection = ds.getConnection();
            statement = prepare(connection, "SELECT size_bytes, content FROM " + table + " WHERE hash_hex=?");
            statement.setString(1, hash);
            resultSet = statement.executeQuery();
            if (!resultSet.next()) {
                throw new IOException("Database artifact disappeared before stream open: " + hash);
            }
            long actualSize = resultSet.getLong(1);
            requireStoredSize(hash, actualSize);
            if (actualSize != expectedSize) {
                throw new IOException("Database artifact size changed before stream open for " + hash + ": expected "
                        + expectedSize + " but found " + actualSize);
            }
            content = resultSet.getBinaryStream(2);
            if (content == null) {
                throw new IOException("Database artifact content is null for " + hash);
            }
            metrics.recordReadOpened();
            return new JdbcArtifactInputStream(content, resultSet, statement, connection, hash, expectedSize,
                    maxArtifactSizeBytes, metrics, startedNanos);
        } catch (SQLException exception) {
            IOException failure = new IOException(
                    "Failed to open database artifact stream " + hash + " using " + databaseDialect, exception);
            closeAfterFailedOpen(content, resultSet, statement, connection, failure);
            metrics.recordReadOpenFailure(ArtifactStoreMetrics.elapsedSince(startedNanos));
            throw failure;
        } catch (IOException | RuntimeException exception) {
            closeAfterFailedOpen(content, resultSet, statement, connection, exception);
            metrics.recordReadOpenFailure(ArtifactStoreMetrics.elapsedSince(startedNanos));
            throw exception;
        }
    }

    @Override
    public boolean exists(String hashHex) throws IOException {
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
        try (Connection connection = ds.getConnection();
                PreparedStatement statement = prepare(connection,
                                                      "SELECT 1 FROM " + table + " WHERE hash_hex=?")) {
            statement.setString(1, hash);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IOException("Failed to check database artifact " + hash + " using " + databaseDialect,
                    exception);
        }
    }

    private void requireStoredSize(String hash, long size) throws IOException {
        if (size < 0) {
            throw new IOException("Database artifact has a negative declared size. hash=" + hash + ", size=" + size);
        }
        try {
            requireAllowedSize(size, maxArtifactSizeBytes);
        } catch (IOException exception) {
            throw new IOException("Database artifact exceeds configured read limit. hash=" + hash + ", "
                    + exception.getMessage(), exception);
        }
    }

    private static long validateMaxArtifactSize(long maxBytes) {
        if (maxBytes < ArtifactStore.UNLIMITED_SIZE) {
            throw new IllegalArgumentException("maxArtifactSizeBytes must be -1 or >= 0");
        }
        return maxBytes;
    }

    private static long minimumLimit(long first, long second) {
        if (first == ArtifactStore.UNLIMITED_SIZE) {
            return second;
        }
        if (second == ArtifactStore.UNLIMITED_SIZE) {
            return first;
        }
        return Math.min(first, second);
    }

    private static void requireAllowedSize(long size, long maxBytes) throws IOException {
        if (maxBytes >= 0 && size > maxBytes) {
            throw new IOException("Artifact size exceeds configured limit. maxBytes=" + maxBytes
                    + ", actualSizeBytes=" + size);
        }
    }

    private static void closeAfterFailedOpen(InputStream content,
                                             ResultSet resultSet,
                                             PreparedStatement statement,
                                             Connection connection,
                                             Throwable failure) {
        closeSuppressing(content, failure);
        closeSuppressing(resultSet, failure);
        closeSuppressing(statement, failure);
        closeSuppressing(connection, failure);
    }

    private static void closeSuppressing(AutoCloseable closeable, Throwable failure) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
