package io.github.gear4jtest.jdbc.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the portable Gear4J migration-history table and its state transitions.
 */
final class MigrationHistoryStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationHistoryStore.class);
    private static final String TABLE_NAME = "gear4j_schema_history";
    private static final String STATE_COLUMN = "migration_state";

    private final String moduleId;
    private final Gear4jDatabaseDialect dialect;
    private final BaselineSchemaValidator schemaValidator;
    private final JdbcStatementOptions statementOptions;

    MigrationHistoryStore(String moduleId,
                          Gear4jDatabaseDialect dialect,
                          BaselineSchemaValidator schemaValidator,
                          JdbcStatementOptions statementOptions) {
        this.moduleId = moduleId;
        this.dialect = dialect;
        this.schemaValidator = schemaValidator;
        this.statementOptions = statementOptions;
    }

    void ensureTableAndStateColumn(Connection connection) throws SQLException {
        ensureTable(connection);
        ensureStateColumn(connection);
    }

    boolean tableExists(Connection connection) throws SQLException {
        return schemaValidator.tableExists(connection, TABLE_NAME);
    }

    boolean hasNoHistory(Connection connection) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "SELECT 1 FROM gear4j_schema_history WHERE module_id=?")) {
            statement.setString(1, moduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return !resultSet.next();
            }
        }
    }

    StoredMigration find(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "SELECT checksum, migration_state FROM gear4j_schema_history "
                                                           + "WHERE module_id=? AND version=?")) {
            statement.setString(1, moduleId);
            statement.setString(2, version);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next()
                        ? new StoredMigration(resultSet.getString(1),
                                parseState(resultSet.getString(2)))
                        : null;
            }
        }
    }

    void insert(Connection connection,
                SchemaMigration migration,
                String checksum,
                SchemaMigrationState state)
            throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "INSERT INTO gear4j_schema_history("
                                                           + "module_id, version, description, checksum, "
                                                           + "migration_state, installed_at) VALUES (?,?,?,?,?,?)")) {
            statement.setString(1, moduleId);
            statement.setString(2, migration.version());
            statement.setString(3, migration.description());
            statement.setString(4, checksum);
            statement.setString(5, state.name());
            dialect.setInstant(statement, 6, Instant.now());
            statement.executeUpdate();
        }
    }

    void updateState(Connection connection, String version, SchemaMigrationState state) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "UPDATE gear4j_schema_history SET migration_state=?, "
                                                           + "installed_at=? WHERE module_id=? AND version=?")) {
            statement.setString(1, state.name());
            dialect.setInstant(statement, 2, Instant.now());
            statement.setString(3, moduleId);
            statement.setString(4, version);
            if (statement.executeUpdate() != 1) {
                throw new SchemaMigrationException("Gear4J schema migration state row is missing for "
                        + moduleId + ":" + version);
            }
        }
    }

    List<SchemaMigrationStatus> statuses(Connection connection) throws SQLException {
        boolean hasState = schemaValidator.columnExists(connection, TABLE_NAME, STATE_COLUMN);
        String stateExpression = hasState ? STATE_COLUMN : "'APPLIED'";
        String sql = "SELECT module_id, version, description, checksum, " + stateExpression
                + ", installed_at FROM gear4j_schema_history WHERE module_id=? ORDER BY installed_at, version";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, moduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<SchemaMigrationStatus> statuses = new ArrayList<>();
                while (resultSet.next()) {
                    statuses.add(new SchemaMigrationStatus(resultSet.getString(1), resultSet.getString(2),
                            resultSet.getString(3), resultSet.getString(4),
                            parseState(resultSet.getString(5)),
                            dialect.getInstant(resultSet, "installed_at")));
                }
                return List.copyOf(statuses);
            }
        }
    }

    boolean deleteIncomplete(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "DELETE FROM gear4j_schema_history "
                                                           + "WHERE module_id=? AND version=? "
                                                           + "AND migration_state<>?")) {
            statement.setString(1, moduleId);
            statement.setString(2, version);
            statement.setString(3, SchemaMigrationState.APPLIED.name());
            return statement.executeUpdate() == 1;
        }
    }

    private void ensureTable(Connection connection) throws SQLException {
        if (tableExists(connection)) {
            return;
        }
        try (Statement statement = createStatement(connection)) {
            statement.execute(historyTableSql());
        } catch (SQLException e) {
            if (tableExists(connection)) {
                LOGGER.debug("[Gear4J] Schema history table was created concurrently");
                return;
            }
            throw e;
        }
    }

    private void ensureStateColumn(Connection connection) throws SQLException {
        if (schemaValidator.columnExists(connection, TABLE_NAME, STATE_COLUMN)) {
            return;
        }
        try (Statement statement = createStatement(connection)) {
            statement.execute(historyStateColumnSql());
        } catch (SQLException e) {
            if (schemaValidator.columnExists(connection, TABLE_NAME, STATE_COLUMN)) {
                LOGGER.debug("[Gear4J] Schema history state column was created concurrently");
                return;
            }
            throw e;
        }
    }

    private String historyTableSql() {
        return switch (dialect) {
            case POSTGRESQL -> "CREATE TABLE IF NOT EXISTS gear4j_schema_history ("
                    + "module_id VARCHAR(100) NOT NULL, "
                    + "version VARCHAR(40) NOT NULL, "
                    + "description VARCHAR(300) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "migration_state VARCHAR(16) NOT NULL DEFAULT 'APPLIED', "
                    + "installed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), "
                    + "PRIMARY KEY (module_id, version))";
            case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS gear4j_schema_history ("
                    + "module_id VARCHAR(100) NOT NULL, "
                    + "version VARCHAR(40) NOT NULL, "
                    + "description VARCHAR(300) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "migration_state VARCHAR(16) NOT NULL DEFAULT 'APPLIED', "
                    + "installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (module_id, version))";
            case ORACLE -> "CREATE TABLE gear4j_schema_history ("
                    + "module_id VARCHAR2(100) NOT NULL, "
                    + "version VARCHAR2(40) NOT NULL, "
                    + "description VARCHAR2(300) NOT NULL, "
                    + "checksum VARCHAR2(64) NOT NULL, "
                    + "migration_state VARCHAR2(16) DEFAULT 'APPLIED' NOT NULL, "
                    + "installed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                    + "CONSTRAINT pk_gear4j_schema_history PRIMARY KEY (module_id, version))";
            case H2 -> "CREATE TABLE IF NOT EXISTS gear4j_schema_history ("
                    + "module_id VARCHAR(100) NOT NULL, "
                    + "version VARCHAR(40) NOT NULL, "
                    + "description VARCHAR(300) NOT NULL, "
                    + "checksum VARCHAR(64) NOT NULL, "
                    + "migration_state VARCHAR(16) NOT NULL DEFAULT 'APPLIED', "
                    + "installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (module_id, version))";
        };
    }

    private String historyStateColumnSql() {
        return switch (dialect) {
            case POSTGRESQL, MYSQL, MARIADB, H2 -> "ALTER TABLE gear4j_schema_history "
                    + "ADD COLUMN migration_state VARCHAR(16) NOT NULL DEFAULT 'APPLIED'";
            case ORACLE -> "ALTER TABLE gear4j_schema_history "
                    + "ADD (migration_state VARCHAR2(16) DEFAULT 'APPLIED' NOT NULL)";
        };
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }

    private Statement createStatement(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        statementOptions.apply(statement);
        return statement;
    }

    private SchemaMigrationState parseState(String value) {
        try {
            return SchemaMigrationState.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SchemaMigrationException("Unsupported Gear4J migration state for module " + moduleId
                    + ": " + value, e);
        }
    }

    record StoredMigration(String checksum, SchemaMigrationState state) {}
}
