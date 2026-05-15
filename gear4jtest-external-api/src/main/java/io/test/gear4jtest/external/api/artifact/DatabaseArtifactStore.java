package io.test.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

public final class DatabaseArtifactStore implements ArtifactStore {
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final String DEFAULT_TABLE = "artifact_store";

    private final DataSource ds;
    private final String table;

    public DatabaseArtifactStore(DataSource ds, String table) {
        this.ds = Objects.requireNonNull(ds, "ds must not be null");
        this.table = requireSqlIdentifier(table == null ? DEFAULT_TABLE : table, "table name");
    }

    private static String requireSqlIdentifier(String value, String label) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value);
        }
        return value;
    }

    private static boolean isUniqueViolation(SQLException e) {
        String state = e.getSQLState();
        int code = e.getErrorCode();
        if ("23505".equals(state)) {
            return true;
        }
        if (code == 1062) {
            return true;
        }
        return e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate");
    }

    @Override
    public String put(byte[] content) throws IOException {
        byte[] stored = Objects.requireNonNull(content, "content must not be null").clone();
        String hash = Hashing.sha256Hex(stored);
        try (var c = ds.getConnection()) {
            try (var ps = c
                    .prepareStatement("INSERT INTO " + table + "(hash_hex,size_bytes,content) VALUES (?,?,?)")) {
                ps.setString(1, hash);
                ps.setLong(2, stored.length);
                ps.setBytes(3, stored);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!isUniqueViolation(e)) {
                    throw e;
                }
            }
            return hash;
        } catch (SQLException e) {
            throw new IOException("DB error", e);
        }
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        String hash = Hashing.requireSha256Hex(hashHex);
        try (var c = ds.getConnection();
                var ps = c.prepareStatement("SELECT size_bytes, content FROM " + table + " WHERE hash_hex=?")) {
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
        try (var c = ds.getConnection(); var ps = c.prepareStatement("SELECT 1 FROM " + table + " WHERE hash_hex=?")) {
            ps.setString(1, hash);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IOException("DB error", e);
        }
    }
}
