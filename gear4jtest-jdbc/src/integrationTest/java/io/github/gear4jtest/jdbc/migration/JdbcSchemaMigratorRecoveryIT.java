package io.github.gear4jtest.jdbc.migration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSchemaMigratorRecoveryIT {
    @Test
    void migrationFailure_shouldBlockAutomaticRetryUntilOperatorPreparesIt() throws Exception {
        // Given
        DataSource dataSource = new DriverManagerBackedDataSource(
                "jdbc:h2:mem:migration-recovery;DB_CLOSE_DELAY=-1", "sa", "");
        Map<String, String> resources = new ConcurrentHashMap<>();
        String failedScript = "CREATE TABLE gear4j_fault_probe(id VARCHAR(36)); THIS IS NOT VALID SQL;";
        String validScript = "CREATE TABLE gear4j_fault_probe(id VARCHAR(36));";
        resources.put("fault/migrations.list", "V1__fault_probe.sql\n");
        resources.put("fault/V1__fault_probe.sql", failedScript);
        JdbcSchemaMigrator migrator = migrator(resources);

        assertThat(migrator.migrationStatuses(dataSource)).isEmpty();
        assertThatThrownBy(() -> migrator.prepareRetry(dataSource, "99"))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("Unknown Gear4J migration");
        assertThatThrownBy(() -> migrator.prepareRetry(dataSource, "1"))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("No incomplete Gear4J migration exists");

        // When / Then
        assertThatThrownBy(() -> migrator.migrate(dataSource))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("STARTED or FAILED marker may be durable");
        assertThat(migrator.migrationStatuses(dataSource))
                .singleElement()
                .satisfies(status -> assertThat(status.state()).isEqualTo(SchemaMigrationState.FAILED));

        assertThatThrownBy(() -> migrator.migrate(dataSource))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("Automatic retry is refused")
                .hasMessageContaining("prepareRetry");
        resources.put("fault/V1__fault_probe.sql", validScript);
        assertThatThrownBy(() -> migrator.prepareRetry(dataSource, "1"))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("Checksum mismatch")
                .hasMessageContaining("retry preparation refused");
        resources.put("fault/V1__fault_probe.sql", failedScript);

        // Given: an operator inspected and removed the partial object.
        dropTableIfPresent(dataSource, "gear4j_fault_probe");

        // When
        migrator.prepareRetry(dataSource, "1");
        resources.put("fault/V1__fault_probe.sql", validScript);
        migrator.migrate(dataSource);

        // Then
        assertThat(migrator.migrationStatuses(dataSource))
                .singleElement()
                .satisfies(status -> assertThat(status.state()).isEqualTo(SchemaMigrationState.APPLIED));
        assertThat(tableExists(dataSource, "gear4j_fault_probe")).isTrue();
        assertThatThrownBy(() -> migrator.prepareRetry(dataSource, "1"))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("Applied Gear4J migration cannot be prepared for retry");
    }

    @Test
    void migrate_shouldUpgradeLegacyHistoryRowsToAppliedState() throws Exception {
        // Given
        DataSource dataSource = new DriverManagerBackedDataSource(
                "jdbc:h2:mem:migration-history-upgrade;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE gear4j_schema_history (
                        module_id VARCHAR(100) NOT NULL,
                        version VARCHAR(40) NOT NULL,
                        description VARCHAR(300) NOT NULL,
                        checksum VARCHAR(64) NOT NULL,
                        installed_at TIMESTAMP NOT NULL,
                        PRIMARY KEY (module_id, version)
                    )
                    """);
            statement.execute("""
                    INSERT INTO gear4j_schema_history(
                        module_id, version, description, checksum, installed_at
                    ) VALUES (
                        'gear4j-legacy-test', '1', 'legacy migration',
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        CURRENT_TIMESTAMP
                    )
                    """);
        }
        JdbcSchemaMigrator migrator = JdbcSchemaMigrator.builder()
                .moduleId("gear4j-legacy-test")
                .dialect(Gear4jDatabaseDialect.H2)
                .migrationListResource("legacy/migrations.list")
                .baselineTableName("legacy_table")
                .classLoader(resources(Map.of("legacy/migrations.list", "")))
                .build();

        // When / Then: inspection is read-only and understands the legacy shape.
        assertThat(migrator.migrationStatuses(dataSource))
                .singleElement()
                .satisfies(status -> assertThat(status.state()).isEqualTo(SchemaMigrationState.APPLIED));

        // When
        migrator.migrate(dataSource);

        // Then
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                                                            "SELECT migration_state FROM gear4j_schema_history "
                                                                    + "WHERE module_id=? AND version=?")) {
            statement.setString(1, "gear4j-legacy-test");
            statement.setString(2, "1");
            try (var resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("APPLIED");
            }
        }
    }

    private static JdbcSchemaMigrator migrator(Map<String, String> resources) {
        return JdbcSchemaMigrator.builder()
                .moduleId("gear4j-recovery-test")
                .dialect(Gear4jDatabaseDialect.H2)
                .migrationListResource("fault/migrations.list")
                .baselineTableName("gear4j_fault_probe")
                .classLoader(resources(resources))
                .build();
    }

    private static void dropTableIfPresent(DataSource dataSource, String tableName) throws SQLException {
        if (!tableExists(dataSource, tableName)) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE " + tableName);
        }
    }

    private static boolean tableExists(DataSource dataSource, String tableName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return tableExists(connection, tableName);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (var resultSet = connection.getMetaData().getTables(null, null, tableName, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (var resultSet = connection.getMetaData().getTables(null, null, tableName.toUpperCase(), null)) {
            return resultSet.next();
        }
    }

    private static ClassLoader resources(Map<String, String> resources) {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                String content = resources.get(name);
                return content == null ? null
                        : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private record DriverManagerBackedDataSource(String url, String username, String password) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
