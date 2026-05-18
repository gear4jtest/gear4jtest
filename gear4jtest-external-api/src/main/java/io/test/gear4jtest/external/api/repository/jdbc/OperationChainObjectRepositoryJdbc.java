package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import io.test.gear4jtest.external.api.ExecutionMode;
import io.test.gear4jtest.external.api.model.OperationChainObject;
import io.test.gear4jtest.external.api.repository.OperationChainObjectRepository;

public final class OperationChainObjectRepositoryJdbc implements OperationChainObjectRepository {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private final DataSource ds;

    public OperationChainObjectRepositoryJdbc(DataSource ds) {
        this.ds = Objects.requireNonNull(ds, "ds must not be null");
    }

    /**
     * @deprecated the repository uses portable JDBC APIs and no longer needs a
     *             dialect hint.
     */
    @Deprecated(forRemoval = true)
    public OperationChainObjectRepositoryJdbc(DataSource ds, JdbcDialect dialect) {
        this(ds);
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setTimestamp(PreparedStatement ps, int index, Instant instant) throws SQLException {
        if (instant == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(index, Timestamp.from(instant));
        }
    }

    private static OperationChainObject map(ResultSet rs) throws SQLException {
        return new OperationChainObject(rs.getLong("id"), rs.getString("al_id"), rs.getString("version"),
                ExecutionMode.valueOf(rs.getString("mode")), requireContentHash(rs.getString("content_hash")),
                rs.getLong("size_bytes"), rs.getString("mime_type"), instantOrNull(rs, "created_at"),
                rs.getString("created_by"), instantOrNull(rs, "published_at"));
    }

    private static String requireContentHash(String contentHash) {
        if (contentHash == null || !SHA_256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 content hash: " + contentHash);
        }
        return contentHash.toLowerCase(Locale.ROOT);
    }

    @Override
    public long insert(OperationChainObject o) {
        String sql = "INSERT INTO operation_chain_object(al_id, version, mode, content_hash, size_bytes, "
                + "mime_type, created_at, created_by, published_at) VALUES (?,?,?,?,?,?,?,?,?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, o.alId());
            ps.setString(2, o.version());
            ps.setString(3, o.mode().name());
            ps.setString(4, requireContentHash(o.contentHash()));
            ps.setLong(5, o.sizeBytes());
            ps.setString(6, o.mimeType());
            setTimestamp(ps, 7, o.createdAt());
            ps.setString(8, o.createdBy());
            setTimestamp(ps, 9, o.publishedAt());
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<OperationChainObject> find(String alId, String version, ExecutionMode mode) {
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at "
                + "FROM operation_chain_object WHERE al_id=? AND version=? AND mode=?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setString(2, version);
            ps.setString(3, mode.name());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<OperationChainObject> findLatestRun(String alId) {
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at "
                + "FROM operation_chain_object WHERE al_id=? AND mode='RUN' ORDER BY published_at DESC, id DESC";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setMaxRows(1);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean exists(String alId, String version, ExecutionMode mode) {
        String sql = "SELECT 1 FROM operation_chain_object WHERE al_id=? AND version=? AND mode=?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setString(2, version);
            ps.setString(3, mode.name());
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public java.util.List<OperationChainObject> findAll(String alId) {
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at "
                + "FROM operation_chain_object WHERE al_id=? ORDER BY published_at DESC, id DESC";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            try (var rs = ps.executeQuery()) {
                var list = new java.util.ArrayList<OperationChainObject>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
