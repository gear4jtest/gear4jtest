package io.test.gear4jtest.external.api.repository.jdbc;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.test.gear4jtest.external.api.repository.OperationChainTagRepository;

public final class OperationChainTagRepositoryJdbc implements OperationChainTagRepository {
    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;

    public OperationChainTagRepositoryJdbc(DataSource ds, Gear4jDatabaseDialect databaseDialect) {
        this.ds = Objects.requireNonNull(ds, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
    }

    static String insertTagSql(Gear4jDatabaseDialect databaseDialect) {
        return ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
    }

    @Override
    public void addTag(String alId, String tag) {
        String sql = ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setString(2, tag);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeTag(String alId, String tag) {
        String sql = "DELETE FROM operation_chain_tag WHERE al_id=? AND tag=?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            ps.setString(2, tag);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> listTags(String alId) {
        String sql = "SELECT tag FROM operation_chain_tag WHERE al_id=? ORDER BY tag";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, alId);
            try (var rs = ps.executeQuery()) {
                Set<String> s = new java.util.LinkedHashSet<>();
                while (rs.next()) {
                    s.add(rs.getString(1));
                }
                return s;
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> findAssemblyLineIdsByTag(String tag) {
        String sql = "SELECT al_id FROM operation_chain_tag WHERE tag=? ORDER BY al_id";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, tag);
            try (var rs = ps.executeQuery()) {
                var list = new java.util.ArrayList<String>();
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
                return list;
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
