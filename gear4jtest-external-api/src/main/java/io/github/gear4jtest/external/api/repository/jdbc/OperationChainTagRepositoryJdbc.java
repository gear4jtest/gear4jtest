package io.github.gear4jtest.external.api.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.JdbcStatementOptions;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;

public final class OperationChainTagRepositoryJdbc implements OperationChainTagRepository {
    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;

    public OperationChainTagRepositoryJdbc(DataSource ds, Gear4jDatabaseDialect databaseDialect) {
        this(ds, databaseDialect, JdbcStatementOptions.defaults());
    }

    public OperationChainTagRepositoryJdbc(DataSource ds,
                                           Gear4jDatabaseDialect databaseDialect,
                                           Duration jdbcStatementTimeout) {
        this(ds, databaseDialect, JdbcStatementOptions.of(jdbcStatementTimeout));
    }

    public OperationChainTagRepositoryJdbc(DataSource ds,
                                           Gear4jDatabaseDialect databaseDialect,
                                           JdbcStatementOptions statementOptions) {
        this.ds = Objects.requireNonNull(ds, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(statementOptions, "statementOptions must not be null");
    }

    static String insertTagSql(Gear4jDatabaseDialect databaseDialect) {
        return ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }

    @Override
    public void addTag(String alId, String tag) {
        String sql = ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
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
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, alId);
            ps.setString(2, tag);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> listTags(String alId) {
        return listTags(alId, null);
    }

    @Override
    public Set<String> listTags(String alId, PageRequest pageRequest) {
        String orderedSql = "SELECT tag FROM operation_chain_tag WHERE al_id=? ORDER BY tag";
        String sql = pageRequest == null ? orderedSql
                : ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, alId);
            if (pageRequest != null) {
                ExternalRepositorySqlDialect.bindPage(databaseDialect, ps, 2, pageRequest);
            }
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
        return findAssemblyLineIdsByTag(tag, null);
    }

    @Override
    public List<String> findAssemblyLineIdsByTag(String tag, PageRequest pageRequest) {
        String orderedSql = "SELECT al_id FROM operation_chain_tag WHERE tag=? ORDER BY al_id";
        String sql = pageRequest == null ? orderedSql
                : ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, tag);
            if (pageRequest != null) {
                ExternalRepositorySqlDialect.bindPage(databaseDialect, ps, 2, pageRequest);
            }
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
