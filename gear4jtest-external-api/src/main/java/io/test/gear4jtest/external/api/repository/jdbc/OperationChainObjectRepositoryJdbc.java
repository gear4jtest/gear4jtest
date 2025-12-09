package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;
import javax.sql.DataSource;

import io.test.gear4jtest.external.api.ExecutionMode;
import io.test.gear4jtest.external.api.model.OperationChainObject;
import io.test.gear4jtest.external.api.repository.OperationChainObjectRepository;

public final class OperationChainObjectRepositoryJdbc implements OperationChainObjectRepository {
    private final DataSource ds;
    private final JdbcDialect dialect;

    public OperationChainObjectRepositoryJdbc(DataSource ds, JdbcDialect dialect) {
        this.ds = ds;
        this.dialect = dialect;
    }

    private static Optional<OperationChainObject> map(ResultSet rs) throws SQLException {
        return Optional.of(new OperationChainObject(
                rs.getLong("id"),
                rs.getString("al_id"),
                rs.getString("version"),
                ExecutionMode.valueOf(rs.getString("mode")),
                rs.getString("content_hash"),
                rs.getLong("size_bytes"),
                rs.getString("mime_type"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("created_by"),
                rs.getTimestamp("published_at").toInstant()
        ));
    }

    @Override
    public long insert(OperationChainObject o) {
        String sql = "INSERT INTO operation_chain_object(al_id,version,mode,content_hash,size_bytes,mime_type,created_at,created_by,published_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?)";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, o.alId());
            ps.setString(2, o.version());
            ps.setString(3, o.mode().name());
            ps.setString(4, o.contentHash());
            ps.setLong(5, o.sizeBytes());
            ps.setString(6, o.mimeType());
            ps.setTimestamp(7, Timestamp.from(o.createdAt()));
            ps.setString(8, o.createdBy());
            ps.setTimestamp(9, Timestamp.from(o.publishedAt()));
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
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at " +
                "FROM operation_chain_object WHERE al_id=? AND version=? AND mode=?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setString(2, version);
            ps.setString(3, mode.name());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<OperationChainObject> findLatestRun(String alId) {
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at " +
                "FROM operation_chain_object WHERE al_id=? AND mode='RUN' ORDER BY published_at DESC, id DESC LIMIT 1";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : Optional.empty();
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
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at " +
                "FROM operation_chain_object WHERE al_id=? ORDER BY published_at DESC, id DESC";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            try (var rs = ps.executeQuery()) {
                var list = new java.util.ArrayList<OperationChainObject>();
                while (rs.next()) {
                    list.add(map(rs).get());
                }
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
