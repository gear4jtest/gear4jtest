package io.github.gear4jtest.jdbc.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns creation and acquisition of the portable migration lock. */
final class MigrationLockStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationLockStore.class);
    private static final String TABLE_NAME = "gear4j_schema_lock";

    private final String moduleId;
    private final Gear4jDatabaseDialect dialect;
    private final BaselineSchemaValidator schemaValidator;
    private final JdbcStatementOptions statementOptions;

    MigrationLockStore(String moduleId,
                       Gear4jDatabaseDialect dialect,
                       BaselineSchemaValidator schemaValidator,
                       JdbcStatementOptions statementOptions) {
        this.moduleId = moduleId;
        this.dialect = dialect;
        this.schemaValidator = schemaValidator;
        this.statementOptions = statementOptions;
    }

    void ensureTableAndRow(Connection connection) throws SQLException {
        ensureTable(connection);
        ensureRow(connection);
    }

    void acquire(Connection connection) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                                                   "SELECT lock_name FROM gear4j_schema_lock "
                                                           + "WHERE lock_name = ? FOR UPDATE")) {
            statement.setString(1, moduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SchemaMigrationException("Gear4J schema lock row is missing for module " + moduleId);
                }
            }
        }
        try (PreparedStatement statement = prepare(connection,
                                                   "UPDATE gear4j_schema_lock SET locked_at = ? "
                                                           + "WHERE lock_name = ?")) {
            dialect.setInstant(statement, 1, Instant.now());
            statement.setString(2, moduleId);
            statement.executeUpdate();
        }
    }

    private void ensureTable(Connection connection) throws SQLException {
        if (schemaValidator.tableExists(connection, TABLE_NAME)) {
            return;
        }
        try (Statement statement = createStatement(connection)) {
            statement.execute(tableSql());
        } catch (SQLException exception) {
            if (schemaValidator.tableExists(connection, TABLE_NAME)) {
                LOGGER.debug("[Gear4J] Schema infrastructure table {} was created concurrently", TABLE_NAME);
                return;
            }
            throw exception;
        }
    }

    private void ensureRow(Connection connection) throws SQLException {
        try (PreparedStatement statement = prepare(connection, rowInsertSql())) {
            statement.setString(1, moduleId);
            dialect.setInstant(statement, 2, Instant.now());
            statement.executeUpdate();
        }
    }

    private String tableSql() {
        return switch (dialect) {
            case POSTGRESQL -> "CREATE TABLE IF NOT EXISTS gear4j_schema_lock ("
                    + "lock_name VARCHAR(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMPTZ NOT NULL DEFAULT NOW())";
            case MYSQL, MARIADB -> "CREATE TABLE IF NOT EXISTS gear4j_schema_lock ("
                    + "lock_name VARCHAR(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";
            case ORACLE -> "CREATE TABLE gear4j_schema_lock ("
                    + "lock_name VARCHAR2(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)";
            case H2 -> "CREATE TABLE IF NOT EXISTS gear4j_schema_lock ("
                    + "lock_name VARCHAR(100) PRIMARY KEY, "
                    + "locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)";
        };
    }

    private String rowInsertSql() {
        return switch (dialect) {
            case POSTGRESQL -> "INSERT INTO gear4j_schema_lock(lock_name, locked_at) VALUES (?,?) "
                    + "ON CONFLICT (lock_name) DO NOTHING";
            case MYSQL, MARIADB -> "INSERT IGNORE INTO gear4j_schema_lock(lock_name, locked_at) VALUES (?,?)";
            case ORACLE -> "MERGE INTO gear4j_schema_lock target "
                    + "USING (SELECT ? lock_name, ? locked_at FROM dual) source "
                    + "ON (target.lock_name = source.lock_name) "
                    + "WHEN NOT MATCHED THEN INSERT (lock_name, locked_at) "
                    + "VALUES (source.lock_name, source.locked_at)";
            case H2 -> "MERGE INTO gear4j_schema_lock(lock_name, locked_at) KEY(lock_name) VALUES (?,?)";
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
}
