package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;

public final class OperationChainConfigRepositoryJdbc implements OperationChainConfigRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;
    private final ObjectMapper objectMapper;
    private final JdbcStatementOptions statementOptions;
    private final JdbcTransactionOperations transactionOperations;

    public static Builder builder() {
        return new Builder();
    }

    private OperationChainConfigRepositoryJdbc(Builder builder) {
        this.ds = Objects.requireNonNull(builder.dataSource, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        this.objectMapper = Objects.requireNonNull(builder.objectMapper, "objectMapper must not be null");
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
        this.transactionOperations = builder.transactionOperations != null
                ? builder.transactionOperations
                : JdbcTransactionOperations.autonomous(ds);
    }

    public static final class Builder {
        private DataSource dataSource;
        private Gear4jDatabaseDialect databaseDialect;
        private ObjectMapper objectMapper = new ObjectMapper();
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

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
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
         * Configures ownership of configuration write transactions. When omitted,
         * Gear4J uses library-owned autonomous transactions.
         */
        public Builder transactionOperations(JdbcTransactionOperations transactionOperations) {
            this.transactionOperations = Objects.requireNonNull(transactionOperations,
                                                                "transactionOperations must not be null");
            return this;
        }

        public OperationChainConfigRepositoryJdbc build() {
            return new OperationChainConfigRepositoryJdbc(this);
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new OperationChainRepositoryException("Failed to serialize operation-chain store properties", e);
        }
    }

    private Map<String, String> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> result = objectMapper.readValue(json, STRING_MAP_TYPE);
            return result == null ? Map.of() : Map.copyOf(result);
        } catch (Exception e) {
            throw new OperationChainRepositoryException("Failed to deserialize operation-chain store properties", e);
        }
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }

    @Override
    public Optional<OperationChainConfig> findByAssemblyLineId(String alId) {
        String sql = "SELECT al_id, allow_run_publication_without_test, store_type, store_props "
                + "FROM operation_chain_config WHERE al_id=?";
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, alId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String id = rs.getString(1);
                boolean allowed = rs.getBoolean(2);
                StoreType type = StoreType.valueOf(rs.getString(3));
                Map<String, String> props = readJsonMap(rs.getString(4));
                return Optional.of(new OperationChainConfig(id, allowed, type, props));
            }
        } catch (SQLException e) {
            throw repositoryFailure("find operation-chain configuration " + alId, e);
        }
    }

    @Override
    public void upsert(OperationChainConfig cfg) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        String sql = ExternalRepositorySqlDialect.upsertOperationChainConfigSql(databaseDialect);
        String storeProperties = toJson(cfg.storeProps());
        boolean allowRunWithoutTest = Boolean.TRUE.equals(cfg.allowRunPublicationWithoutTest());
        try {
            transactionOperations.execute(connection -> {
                try (PreparedStatement statement = prepare(connection, sql)) {
                    statement.setString(1, cfg.alId());
                    ExternalRepositorySqlDialect.setBoolean(databaseDialect, statement, 2, allowRunWithoutTest);
                    statement.setString(3, cfg.storeType().name());
                    ExternalRepositorySqlDialect.setJsonText(databaseDialect, statement, 4, storeProperties);
                    statement.executeUpdate();
                }
            });
        } catch (SQLException e) {
            throw repositoryFailure("upsert operation-chain configuration " + cfg.alId(), e);
        }
    }

    @Override
    public void setAllowRunPublicationWithoutTest(String alId, boolean allowed) {
        String sql = "UPDATE operation_chain_config SET allow_run_publication_without_test=? WHERE al_id=?";
        try {
            transactionOperations.execute(connection -> {
                try (PreparedStatement statement = prepare(connection, sql)) {
                    ExternalRepositorySqlDialect.setBoolean(databaseDialect, statement, 1, allowed);
                    statement.setString(2, alId);
                    requireSingleUpdatedRow(statement.executeUpdate(), "publication policy", alId);
                }
            });
        } catch (SQLException e) {
            throw repositoryFailure("update publication policy for " + alId, e);
        }
    }

    @Override
    public void updateStore(String alId, StoreType storeType, Map<String, String> storeProps) {
        Objects.requireNonNull(storeType, "storeType must not be null");
        String sql = ExternalRepositorySqlDialect.updateOperationChainStoreSql(databaseDialect);
        String storeProperties = toJson(storeProps);
        try {
            transactionOperations.execute(connection -> {
                try (PreparedStatement statement = prepare(connection, sql)) {
                    statement.setString(1, storeType.name());
                    ExternalRepositorySqlDialect.setJsonText(databaseDialect, statement, 2, storeProperties);
                    statement.setString(3, alId);
                    requireSingleUpdatedRow(statement.executeUpdate(), "artifact store", alId);
                }
            });
        } catch (SQLException e) {
            throw repositoryFailure("update artifact store for " + alId, e);
        }
    }

    private OperationChainRepositoryException repositoryFailure(String operation, SQLException cause) {
        return new OperationChainRepositoryException("Failed to " + operation + " using " + databaseDialect, cause);
    }

    private static void requireSingleUpdatedRow(int updatedRows, String field, String assemblyLineId) {
        if (updatedRows == 0) {
            throw new OperationChainNotFoundException("Cannot update " + field
                    + ": no operation-chain configuration exists for " + assemblyLineId);
        }
        if (updatedRows != 1) {
            throw new OperationChainRepositoryException("Cannot update " + field + " for " + assemblyLineId
                    + ": expected one row but updated " + updatedRows);
        }
    }
}
