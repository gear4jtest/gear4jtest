package io.github.gear4jtest.external.api.repository.jdbc;

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
import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.JdbcStatementOptions;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;

public final class OperationChainConfigRepositoryJdbc implements OperationChainConfigRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;
    private final ObjectMapper objectMapper;
    private final JdbcStatementOptions statementOptions;

    public OperationChainConfigRepositoryJdbc(DataSource ds, Gear4jDatabaseDialect databaseDialect) {
        this(ds, databaseDialect, new ObjectMapper(), JdbcStatementOptions.defaults());
    }

    public OperationChainConfigRepositoryJdbc(DataSource ds,
                                              Gear4jDatabaseDialect databaseDialect,
                                              ObjectMapper objectMapper) {
        this(ds, databaseDialect, objectMapper, JdbcStatementOptions.defaults());
    }

    public OperationChainConfigRepositoryJdbc(DataSource ds,
                                              Gear4jDatabaseDialect databaseDialect,
                                              ObjectMapper objectMapper,
                                              Duration jdbcStatementTimeout) {
        this(ds, databaseDialect, objectMapper, JdbcStatementOptions.of(jdbcStatementTimeout));
    }

    public OperationChainConfigRepositoryJdbc(DataSource ds,
                                              Gear4jDatabaseDialect databaseDialect,
                                              ObjectMapper objectMapper,
                                              JdbcStatementOptions statementOptions) {
        this.ds = Objects.requireNonNull(ds, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.statementOptions = Objects.requireNonNull(statementOptions, "statementOptions must not be null");
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map == null ? Map.of() : map);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid store properties map", e);
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
            throw new IllegalArgumentException("Invalid JSON map", e);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public void upsert(OperationChainConfig cfg) {
        Objects.requireNonNull(cfg, "cfg must not be null");
        String sql = ExternalRepositorySqlDialect.upsertOperationChainConfigSql(databaseDialect);
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, cfg.alId());
            ExternalRepositorySqlDialect.setBoolean(databaseDialect, ps, 2,
                                                    Boolean.TRUE.equals(cfg.allowRunPublicationWithoutTest()));
            ps.setString(3, cfg.storeType().name());
            ExternalRepositorySqlDialect.setJsonText(databaseDialect, ps, 4, toJson(cfg.storeProps()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setAllowRunPublicationWithoutTest(String alId, boolean allowed) {
        String sql = "UPDATE operation_chain_config SET allow_run_publication_without_test=? WHERE al_id=?";
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ExternalRepositorySqlDialect.setBoolean(databaseDialect, ps, 1, allowed);
            ps.setString(2, alId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateStore(String alId, StoreType storeType, Map<String, String> storeProps) {
        Objects.requireNonNull(storeType, "storeType must not be null");
        String sql = ExternalRepositorySqlDialect.updateOperationChainStoreSql(databaseDialect);
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, storeType.name());
            ExternalRepositorySqlDialect.setJsonText(databaseDialect, ps, 2, toJson(storeProps));
            ps.setString(3, alId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
