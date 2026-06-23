package io.github.gear4jtest.jdbc.migration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSchemaMigratorTest {
    @Test
    void splitSqlStatements_shouldKeepSemicolonsInsideLiteralsAndCommentsOut() {
        // Given
        String script = """
                -- create first table; comment semicolon must be ignored
                CREATE TABLE sample(id INT, label VARCHAR(255) DEFAULT 'a;b');
                /* block comment; must be ignored */
                INSERT INTO sample(id, label) VALUES (1, 'it''s; fine');
                """;

        // When
        var statements = JdbcSchemaMigrator.splitSqlStatements(script);

        // Then
        assertThat(statements)
                .as("SQL semicolons inside comments and quoted literals must not split statements")
                .containsExactly(
                                 "CREATE TABLE sample(id INT, label VARCHAR(255) DEFAULT 'a;b')",
                                 "INSERT INTO sample(id, label) VALUES (1, 'it''s; fine')");
    }

    @Test
    void splitSqlStatements_shouldKeepPostgresqlDollarQuotedBlocksTogether() {
        // Given
        String script = """
                CREATE FUNCTION demo() RETURNS void AS $$
                BEGIN
                    RAISE NOTICE 'a;b';
                END;
                $$ LANGUAGE plpgsql;
                CREATE TABLE after_function(id INT);
                """;

        // When
        var statements = JdbcSchemaMigrator.splitSqlStatements(script);

        // Then
        assertThat(statements)
                .as("PostgreSQL dollar-quoted bodies may contain semicolons")
                .hasSize(2);
        assertThat(statements.get(0))
                .as("function body should remain a single statement")
                .contains("RAISE NOTICE 'a;b';")
                .endsWith("LANGUAGE plpgsql");
        assertThat(statements.get(1))
                .as("statement after the function should still be parsed")
                .isEqualTo("CREATE TABLE after_function(id INT)");
    }

    @Test
    void coreV1Migration_shouldIncludeExecutionHistoryIndexesForEveryDialect() throws Exception {
        for (Gear4jDatabaseDialect dialect : Gear4jDatabaseDialect.values()) {
            // Given
            String basePath = "io/github/gear4j/db/" + dialect.resourceDirectory() + "/migrations/";

            // When
            String listContent;
            try (InputStream list = JdbcSchemaMigrator.class.getClassLoader()
                    .getResourceAsStream(basePath + "migrations.list")) {
                assertThat(list).as("migration list for %s", dialect).isNotNull();
                listContent = new String(list.readAllBytes(), StandardCharsets.UTF_8);
            }
            String v1Content;
            try (InputStream migration = JdbcSchemaMigrator.class.getClassLoader()
                    .getResourceAsStream(basePath + "V1__create_execution_schema.sql")) {
                assertThat(migration).as("V1 migration for %s", dialect).isNotNull();
                v1Content = new String(migration.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Then
            assertThat(listContent).as("migration list for %s", dialect)
                    .contains("V1__create_execution_schema.sql")
                    .doesNotContain("V2__add_execution_history_indexes.sql");
            assertThat(v1Content).as("V1 migration for %s", dialect)
                    .contains("idx_ar_assembly_line_start")
                    .contains("idx_ar_status_start");
        }
    }

    @Test
    void migrate_shouldUseTransactionAndSchemaLockWhenConnectionOwnsAutoCommit() throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement insertLock = mock(PreparedStatement.class);
        PreparedStatement selectLock = mock(PreparedStatement.class);
        PreparedStatement updateLock = mock(PreparedStatement.class);
        ResultSet lockRow = resultSet(true);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(isNull(), isNull(), anyString(), isNull())).thenAnswer(invocation -> resultSet(true));
        when(connection.prepareStatement("INSERT INTO gear4j_schema_lock(lock_name, locked_at) VALUES (?,?) "
                + "ON CONFLICT (lock_name) DO NOTHING"))
                .thenReturn(insertLock);
        when(connection.prepareStatement("SELECT lock_name FROM gear4j_schema_lock WHERE lock_name = ? FOR UPDATE"))
                .thenReturn(selectLock);
        when(selectLock.executeQuery()).thenReturn(lockRow);
        when(connection.prepareStatement("UPDATE gear4j_schema_lock SET locked_at = ? WHERE lock_name = ?"))
                .thenReturn(updateLock);
        var migrator = JdbcSchemaMigrator.builder()
                .moduleId("gear4j-core")
                .dialect(Gear4jDatabaseDialect.POSTGRESQL)
                .migrationListResource("db/migrations.list")
                .baselineTableName("assembly_run")
                .classLoader(resources(Map.of("db/migrations.list", "")))
                .build();

        // When
        migrator.migrate(connection);

        // Then
        InOrder order = inOrder(connection, selectLock);
        order.verify(connection).setAutoCommit(false);
        verify(selectLock).setQueryTimeout(30);
        order.verify(selectLock).executeQuery();
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void migrate_shouldRollbackWhenBaselineSchemaIsIncomplete() throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement insertLock = mock(PreparedStatement.class);
        PreparedStatement selectLock = mock(PreparedStatement.class);
        PreparedStatement updateLock = mock(PreparedStatement.class);
        PreparedStatement hasHistory = mock(PreparedStatement.class);
        ResultSet selectedLock = resultSet(true);
        ResultSet emptyHistory = resultSet(false);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(isNull(), isNull(), anyString(), isNull())).thenAnswer(invocation -> {
            String table = invocation.getArgument(2, String.class);
            boolean exists = List.of("gear4j_schema_history", "gear4j_schema_lock", "assembly_run")
                    .contains(table.toLowerCase());
            return resultSet(exists);
        });
        when(connection.prepareStatement("INSERT INTO gear4j_schema_lock(lock_name, locked_at) VALUES (?,?) "
                + "ON CONFLICT (lock_name) DO NOTHING"))
                .thenReturn(insertLock);
        when(connection.prepareStatement("SELECT lock_name FROM gear4j_schema_lock WHERE lock_name = ? FOR UPDATE"))
                .thenReturn(selectLock);
        when(selectLock.executeQuery()).thenReturn(selectedLock);
        when(connection.prepareStatement("UPDATE gear4j_schema_lock SET locked_at = ? WHERE lock_name = ?"))
                .thenReturn(updateLock);
        when(connection.prepareStatement("SELECT 1 FROM gear4j_schema_history WHERE module_id=?"))
                .thenReturn(hasHistory);
        when(hasHistory.executeQuery()).thenReturn(emptyHistory);
        var resources = Map.of(
                               "db/migrations.list", "V1__create_execution_schema.sql\n",
                               "db/V1__create_execution_schema.sql",
                               """
                                       CREATE TABLE assembly_run(id VARCHAR(36), assembly_line_id VARCHAR(255), status VARCHAR(50), start_time TIMESTAMP);
                                       CREATE TABLE station_log(id VARCHAR(36), assembly_line_execution_id VARCHAR(36), operation_id VARCHAR(255), status VARCHAR(50), start_time TIMESTAMP);
                                       """);
        var migrator = JdbcSchemaMigrator.builder()
                .moduleId("gear4j-core")
                .dialect(Gear4jDatabaseDialect.POSTGRESQL)
                .migrationListResource("db/migrations.list")
                .baselineTableName("assembly_run")
                .classLoader(resources(resources))
                .build();

        // When / Then
        assertThatThrownBy(() -> migrator.migrate(connection))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("Missing expected table(s)")
                .hasMessageContaining("station_log");
        InOrder order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void builder_shouldRejectBlankRequiredValuesAndNullOptions() {
        assertThatThrownBy(() -> JdbcSchemaMigrator.builder()
                .moduleId(" ")
                .dialect(Gear4jDatabaseDialect.H2)
                .migrationListResource("db/migrations.list")
                .baselineTableName("assembly_run")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("moduleId must not be blank");

        assertThatThrownBy(() -> JdbcSchemaMigrator.builder()
                .moduleId("module")
                .dialect(Gear4jDatabaseDialect.H2)
                .migrationListResource(" ")
                .baselineTableName("assembly_run")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("migrationListResource must not be blank");

        assertThatThrownBy(() -> JdbcSchemaMigrator.builder()
                .moduleId("module")
                .dialect(Gear4jDatabaseDialect.H2)
                .migrationListResource("db/migrations.list")
                .baselineTableName(" ")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baselineTableName must not be blank");

        assertThatThrownBy(() -> JdbcSchemaMigrator.builder()
                .moduleId("module")
                .dialect(Gear4jDatabaseDialect.H2)
                .migrationListResource("db/migrations.list")
                .baselineTableName("assembly_run")
                .statementOptions(null)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("statementOptions must not be null");
    }

    @Test
    void migrateDataSource_shouldOpenMigrateAndCloseConnection() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement insertLock = mock(PreparedStatement.class);
        PreparedStatement selectLock = mock(PreparedStatement.class);
        PreparedStatement updateLock = mock(PreparedStatement.class);
        ResultSet selectedLock = resultSet(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getTables(isNull(), isNull(), anyString(), isNull())).thenAnswer(invocation -> resultSet(true));
        when(connection.prepareStatement("INSERT INTO gear4j_schema_lock(lock_name, locked_at) VALUES (?,?) "
                + "ON CONFLICT (lock_name) DO NOTHING"))
                .thenReturn(insertLock);
        when(connection.prepareStatement("SELECT lock_name FROM gear4j_schema_lock WHERE lock_name = ? FOR UPDATE"))
                .thenReturn(selectLock);
        when(selectLock.executeQuery()).thenReturn(selectedLock);
        when(connection.prepareStatement("UPDATE gear4j_schema_lock SET locked_at = ? WHERE lock_name = ?"))
                .thenReturn(updateLock);
        JdbcSchemaMigrator migrator = JdbcSchemaMigrator.builder()
                .moduleId("gear4j-core")
                .dialect(Gear4jDatabaseDialect.POSTGRESQL)
                .migrationListResource("/db/migrations.list")
                .baselineTableName("assembly_run")
                .classLoader(resources(Map.of("db/migrations.list", "")))
                .build();

        migrator.migrate(dataSource);

        verify(connection).close();
        verify(selectLock).executeQuery();
    }

    @Test
    void migrateDataSource_shouldWrapConnectionFailures() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        SQLException failure = new SQLException("no connection");
        when(dataSource.getConnection()).thenThrow(failure);
        JdbcSchemaMigrator migrator = JdbcSchemaMigrator.builder()
                .moduleId("gear4j-core")
                .dialect(Gear4jDatabaseDialect.H2)
                .migrationListResource("db/migrations.list")
                .baselineTableName("assembly_run")
                .classLoader(resources(Map.of("db/migrations.list", "")))
                .build();

        assertThatThrownBy(() -> migrator.migrate(dataSource))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("Failed to migrate Gear4J schema for module gear4j-core")
                .hasCause(failure);
    }

    private static ResultSet resultSet(boolean next) throws SQLException {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(next, false);
        return resultSet;
    }

    private static ClassLoader resources(Map<String, String> resources) {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                String content = resources.get(name);
                if (content == null) {
                    return null;
                }
                return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
