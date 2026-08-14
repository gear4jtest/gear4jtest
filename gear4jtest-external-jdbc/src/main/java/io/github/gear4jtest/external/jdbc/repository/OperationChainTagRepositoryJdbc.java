package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationTags;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;

public final class OperationChainTagRepositoryJdbc implements OperationChainTagRepository {
    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;
    private final JdbcTransactionOperations transactionOperations;

    public static Builder builder() {
        return new Builder();
    }

    private OperationChainTagRepositoryJdbc(Builder builder) {
        this.ds = Objects.requireNonNull(builder.dataSource, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
        this.transactionOperations = builder.transactionOperations != null
                ? builder.transactionOperations
                : JdbcTransactionOperations.autonomous(ds);
    }

    public static final class Builder {
        private DataSource dataSource;
        private Gear4jDatabaseDialect databaseDialect;
        private JdbcStatementOptions statementOptions = JdbcStatementOptions.defaults();
        private JdbcTransactionOperations transactionOperations;

        private Builder() {
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
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

        /**
         * Configures ownership of tag write transactions. When omitted, Gear4J uses
         * library-owned autonomous transactions.
         */
        public Builder transactionOperations(JdbcTransactionOperations transactionOperations) {
            this.transactionOperations = Objects.requireNonNull(transactionOperations,
                                                                "transactionOperations must not be null");
            return this;
        }

        public OperationChainTagRepositoryJdbc build() {
            return new OperationChainTagRepositoryJdbc(this);
        }
    }

    static String insertTagSql(Gear4jDatabaseDialect databaseDialect) {
        return ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }

    @Override
    public void addTag(String alId, String tag) {
        String requiredTag = OperationChainPublicationTags.requireValidTag(tag);
        String sql = ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
        try {
            transactionOperations.execute(connection -> {
                try (PreparedStatement statement = prepare(connection, sql)) {
                    statement.setString(1, alId);
                    statement.setString(2, requiredTag);
                    statement.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw repositoryFailure("add tag " + requiredTag + " to " + alId, e);
        }
    }

    @Override
    public void removeTag(String alId, String tag) {
        String requiredTag = OperationChainPublicationTags.requireValidTag(tag);
        String sql = "DELETE FROM operation_chain_tag WHERE al_id=? AND tag=?";
        try {
            transactionOperations.execute(connection -> {
                try (PreparedStatement statement = prepare(connection, sql)) {
                    statement.setString(1, alId);
                    statement.setString(2, requiredTag);
                    statement.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw repositoryFailure("remove tag " + requiredTag + " from " + alId, e);
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
            throw repositoryFailure("list tags for " + alId, e);
        }
    }

    @Override
    public List<String> findAssemblyLineIdsByTag(String tag) {
        return findAssemblyLineIdsByTag(tag, null);
    }

    @Override
    public List<String> findAssemblyLineIdsByTag(String tag, PageRequest pageRequest) {
        String requiredTag = OperationChainPublicationTags.requireValidTag(tag);
        String orderedSql = "SELECT al_id FROM operation_chain_tag WHERE tag=? ORDER BY al_id";
        String sql = pageRequest == null ? orderedSql
                : ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, requiredTag);
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
            throw repositoryFailure("find operation chains for tag " + requiredTag, e);
        }
    }

    private OperationChainRepositoryException repositoryFailure(String operation, SQLException cause) {
        return new OperationChainRepositoryException("Failed to " + operation + " using " + databaseDialect, cause);
    }
}
