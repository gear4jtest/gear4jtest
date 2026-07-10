package io.github.gear4jtest.external.jdbc.repository;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;

/**
 * SQL and JDBC behavior needed by the external-api JDBC repositories for a
 * configured database dialect.
 */
public final class ExternalRepositorySqlDialect {
    private ExternalRepositorySqlDialect() {
    }

    static String insertTagIfAbsentSql(Gear4jDatabaseDialect dialect) {
        return switch (requireDialect(dialect)) {
            case POSTGRESQL -> "INSERT INTO operation_chain_tag(al_id, tag) VALUES (?,?) "
                    + "ON CONFLICT (al_id, tag) DO NOTHING";
            case MYSQL, MARIADB -> "INSERT INTO operation_chain_tag(al_id, tag) VALUES (?,?) "
                    + "ON DUPLICATE KEY UPDATE tag=VALUES(tag)";
            case ORACLE -> "MERGE INTO operation_chain_tag target "
                    + "USING (SELECT ? AS al_id, ? AS tag FROM dual) source "
                    + "ON (target.al_id=source.al_id AND target.tag=source.tag) "
                    + "WHEN NOT MATCHED THEN INSERT (al_id, tag) VALUES (source.al_id, source.tag)";
            case H2 -> "MERGE INTO operation_chain_tag (al_id, tag) KEY(al_id, tag) VALUES (?,?)";
        };
    }

    static String upsertOperationChainConfigSql(Gear4jDatabaseDialect dialect) {
        return switch (requireDialect(dialect)) {
            case POSTGRESQL ->
                "INSERT INTO operation_chain_config(al_id, allow_run_publication_without_test, store_type, store_props) "
                        + "VALUES (?,?,?,CAST(? AS JSONB)) "
                        + "ON CONFLICT (al_id) DO UPDATE SET "
                        + "allow_run_publication_without_test=EXCLUDED.allow_run_publication_without_test, "
                        + "store_type=EXCLUDED.store_type, store_props=EXCLUDED.store_props, updated_at=CURRENT_TIMESTAMP";
            case MYSQL, MARIADB ->
                "INSERT INTO operation_chain_config(al_id, allow_run_publication_without_test, store_type, store_props) "
                        + "VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE allow_run_publication_without_test=VALUES(allow_run_publication_without_test), "
                        + "store_type=VALUES(store_type), store_props=VALUES(store_props)";
            case ORACLE -> "MERGE INTO operation_chain_config target "
                    + "USING (SELECT ? AS al_id, ? AS allow_run_publication_without_test, ? AS store_type, ? AS store_props FROM dual) source "
                    + "ON (target.al_id=source.al_id) "
                    + "WHEN MATCHED THEN UPDATE SET target.allow_run_publication_without_test=source.allow_run_publication_without_test, "
                    + "target.store_type=source.store_type, target.store_props=source.store_props, target.updated_at=CURRENT_TIMESTAMP "
                    + "WHEN NOT MATCHED THEN INSERT (al_id, allow_run_publication_without_test, store_type, store_props) "
                    + "VALUES (source.al_id, source.allow_run_publication_without_test, source.store_type, source.store_props)";
            case H2 ->
                "MERGE INTO operation_chain_config (al_id, allow_run_publication_without_test, store_type, store_props) "
                        + "KEY(al_id) VALUES (?,?,?,?)";
        };
    }

    static String updateOperationChainStoreSql(Gear4jDatabaseDialect dialect) {
        return switch (requireDialect(dialect)) {
            case POSTGRESQL ->
                "UPDATE operation_chain_config SET store_type=?, store_props=CAST(? AS JSONB) WHERE al_id=?";
            case MYSQL, MARIADB, ORACLE, H2 ->
                "UPDATE operation_chain_config SET store_type=?, store_props=? WHERE al_id=?";
        };
    }

    static String pagedSql(Gear4jDatabaseDialect dialect, String orderedSql) {
        return requireDialect(dialect) == Gear4jDatabaseDialect.ORACLE
                ? orderedSql + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
                : orderedSql + " LIMIT ? OFFSET ?";
    }

    static void bindPage(Gear4jDatabaseDialect dialect,
                         PreparedStatement statement,
                         int firstParameterIndex,
                         PageRequest pageRequest)
            throws SQLException {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        if (requireDialect(dialect) == Gear4jDatabaseDialect.ORACLE) {
            statement.setInt(firstParameterIndex, pageRequest.offset());
            statement.setInt(firstParameterIndex + 1, pageRequest.limit());
        } else {
            statement.setInt(firstParameterIndex, pageRequest.limit());
            statement.setInt(firstParameterIndex + 1, pageRequest.offset());
        }
    }

    static PreparedStatement prepareGeneratedKeyInsert(Gear4jDatabaseDialect dialect,
                                                       Connection connection,
                                                       String sql)
            throws SQLException {
        if (requireDialect(dialect) == Gear4jDatabaseDialect.ORACLE) {
            return connection.prepareStatement(sql, new String[] { "ID" });
        }
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    }

    static void setBoolean(Gear4jDatabaseDialect dialect, PreparedStatement statement, int index, boolean value)
            throws SQLException {
        if (requireDialect(dialect) == Gear4jDatabaseDialect.ORACLE) {
            statement.setInt(index, value ? 1 : 0);
        } else {
            statement.setBoolean(index, value);
        }
    }

    static void setJsonText(Gear4jDatabaseDialect dialect, PreparedStatement statement, int index, String json)
            throws SQLException {
        if (requireDialect(dialect) == Gear4jDatabaseDialect.ORACLE) {
            statement.setCharacterStream(index, new StringReader(json), json.length());
        } else {
            statement.setString(index, json);
        }
    }

    public static boolean isUniqueViolation(Gear4jDatabaseDialect dialect, SQLException exception) {
        Gear4jDatabaseDialect requiredDialect = requireDialect(dialect);
        String state = exception.getSQLState();
        int code = exception.getErrorCode();
        if ("23505".equals(state)) {
            return true;
        }
        if ((requiredDialect == Gear4jDatabaseDialect.MYSQL
                || requiredDialect == Gear4jDatabaseDialect.MARIADB) && code == 1062) {
            return true;
        }
        if (requiredDialect == Gear4jDatabaseDialect.ORACLE && code == 1) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("duplicate");
    }

    private static Gear4jDatabaseDialect requireDialect(Gear4jDatabaseDialect dialect) {
        return Objects.requireNonNull(dialect, "databaseDialect must not be null");
    }
}
