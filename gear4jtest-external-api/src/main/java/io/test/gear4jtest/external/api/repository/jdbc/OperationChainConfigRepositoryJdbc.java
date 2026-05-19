package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.test.gear4jtest.external.api.StoreType;
import io.test.gear4jtest.external.api.model.OperationChainConfig;
import io.test.gear4jtest.external.api.repository.OperationChainConfigRepository;

public final class OperationChainConfigRepositoryJdbc implements OperationChainConfigRepository {
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private final DataSource ds;
    private final JdbcDialect dialect;
    private final ObjectMapper objectMapper;

    public OperationChainConfigRepositoryJdbc(DataSource ds, JdbcDialect dialect) {
        this(ds, dialect, new ObjectMapper());
    }

    public OperationChainConfigRepositoryJdbc(DataSource ds, JdbcDialect dialect, ObjectMapper objectMapper) {
        this.ds = Objects.requireNonNull(ds, "ds must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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
            throw new IllegalArgumentException("Invalid JSON map: " + json, e);
        }
    }

    @Override
    public Optional<OperationChainConfig> findByAssemblyLineId(String alId) {
        String sql = "SELECT al_id, allow_run_publication_without_test, store_type, store_props FROM operation_chain_config WHERE al_id=?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
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
        switch (dialect) {
            case POSTGRES -> {
                String sql = "INSERT INTO operation_chain_config(al_id, allow_run_publication_without_test, store_type, store_props) "
                        + "VALUES (?,?,?,to_jsonb(?::json)) "
                        + "ON CONFLICT (al_id) DO UPDATE SET "
                        + "allow_run_publication_without_test=EXCLUDED.allow_run_publication_without_test, "
                        + "store_type=EXCLUDED.store_type, store_props=EXCLUDED.store_props";
                try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
                    ps.setString(1, cfg.alId());
                    ps.setBoolean(2, Boolean.TRUE.equals(cfg.allowRunPublicationWithoutTest()));
                    ps.setString(3, cfg.storeType().name());
                    ps.setString(4, toJson(cfg.storeProps()));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            case MYSQL8, MARIADB -> {
                String sql = "INSERT INTO operation_chain_config(al_id, allow_run_publication_without_test, store_type, store_props) "
                        + "VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE allow_run_publication_without_test=VALUES(allow_run_publication_without_test), "
                        + "store_type=VALUES(store_type), store_props=VALUES(store_props)";
                try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
                    ps.setString(1, cfg.alId());
                    ps.setBoolean(2, Boolean.TRUE.equals(cfg.allowRunPublicationWithoutTest()));
                    ps.setString(3, cfg.storeType().name());
                    ps.setString(4, toJson(cfg.storeProps()));
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public void setAllowRunPublicationWithoutTest(String alId, boolean allowed) {
        String sql = "UPDATE operation_chain_config SET allow_run_publication_without_test=? WHERE al_id=?";
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, allowed);
            ps.setString(2, alId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateStore(String alId, StoreType storeType, Map<String, String> storeProps) {
        String sqlPg = "UPDATE operation_chain_config SET store_type=?, store_props=to_jsonb(?::json) WHERE al_id=?";
        String sqlMy = "UPDATE operation_chain_config SET store_type=?, store_props=? WHERE al_id=?";
        String sql = dialect.isPostgres() ? sqlPg : sqlMy;
        try (var c = ds.getConnection(); var ps = c.prepareStatement(sql)) {
            ps.setString(1, storeType.name());
            ps.setString(2, toJson(storeProps));
            ps.setString(3, alId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
