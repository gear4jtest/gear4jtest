package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import io.test.gear4jtest.external.api.StoreType;
import io.test.gear4jtest.external.api.model.OperationChainConfig;
import io.test.gear4jtest.external.api.repository.OperationChainConfigRepository;

public final class OperationChainConfigRepositoryJdbc implements OperationChainConfigRepository {
    private final DataSource ds;
    private final JdbcDialect dialect;

    public OperationChainConfigRepositoryJdbc(DataSource ds, JdbcDialect dialect) {
        this.ds = ds;
        this.dialect = dialect;
    }

    private static String toJson(Map<String, String> map) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            sb.append('"').append(escape(e.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static Map<String, String> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Map.of();
        }
        Map<String, String> out = new java.util.HashMap<>();
        String s = json.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1);
        }
        if (s.isBlank()) {
            return java.util.Map.of();
        }
        for (String pair : s.split(",")) {
            int colon = pair.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String k = strip(pair.substring(0, colon));
            String v = strip(pair.substring(colon + 1));
            out.put(unq(k), unq(v));
        }
        return out;
    }

    private static String strip(String s) {
        return s.trim();
    }

    private static String unq(String s) {
        return s.replaceAll("^\\\"|\\\"$", "").replace("\\\"", "\"");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
                String sql = "INSERT INTO operation_chain_config(al_id, allow_run_publication_without_test, store_type, store_props) " +
                        "VALUES (?,?,?,to_jsonb(?::json)) " +
                        "ON CONFLICT (al_id) DO UPDATE SET allow_run_publication_without_test=EXCLUDED.allow_run_publication_without_test, " +
                        "store_type=EXCLUDED.store_type, store_props=EXCLUDED.store_props";
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
            case MYSQL8 -> {
                String sql = "INSERT INTO operation_chain_config(al_id, allow_run_publication_without_test, store_type, store_props) " +
                        "VALUES (?,?,?,CAST(? AS JSON)) " +
                        "ON DUPLICATE KEY UPDATE allow_run_publication_without_test=VALUES(allow_run_publication_without_test), " +
                        "store_type=VALUES(store_type), store_props=VALUES(store_props)";
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
        String sqlMy = "UPDATE operation_chain_config SET store_type=?, store_props=CAST(? AS JSON) WHERE al_id=?";
        String sql = dialect == JdbcDialect.POSTGRES ? sqlPg : sqlMy;
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
