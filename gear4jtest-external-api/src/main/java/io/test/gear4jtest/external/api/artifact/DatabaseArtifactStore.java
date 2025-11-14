package io.test.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

public final class DatabaseArtifactStore implements ArtifactStore {
    private final DataSource ds;
    private final String table;

    public DatabaseArtifactStore(DataSource ds, String table) {
        this.ds = ds;
        this.table = table == null ? "artifact_store" : table;
    }

    private static boolean isUniqueViolation(SQLException e) {
        String state = e.getSQLState();
        int code = e.getErrorCode();
        if ("23505".equals(state)) return true;
        if (code == 1062) return true;
        return e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate");
    }

    @Override
    public String put(byte[] content) throws IOException {
        String hash = Hashing.sha256Hex(content);
        try (var c = ds.getConnection()) {
            try (var ps = c.prepareStatement("INSERT INTO " + table + "(hash_hex,size_bytes,content) VALUES (?,?,?)")) {
                ps.setString(1, hash);
                ps.setLong(2, content.length);
                ps.setBytes(3, content);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (!isUniqueViolation(e)) throw e;
            }
            return hash;
        } catch (SQLException e) {
            throw new IOException("DB error", e);
        }
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("SELECT size_bytes, content FROM " + table + " WHERE hash_hex=?")) {
            ps.setString(1, hashHex);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                long size = rs.getLong(1);
                byte[] bytes = rs.getBytes(2);
                return Optional.of(new Artifact(hashHex, size, Map.of(), () -> new java.io.ByteArrayInputStream(bytes)));
            }
        } catch (SQLException e) {
            throw new IOException("DB error", e);
        }
    }

    @Override
    public boolean exists(String hashHex) throws IOException {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement("SELECT 1 FROM " + table + " WHERE hash_hex=?")) {
            ps.setString(1, hashHex);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IOException("DB error", e);
        }
    }
}
