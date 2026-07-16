package io.github.gear4jtest.jdbc.persistence;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.sql.DataSource;

import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.migration.JdbcSchemaMigrator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
class DatabaseAssemblyRunRepositoryMultiDialectIT {
    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    void repository_shouldMigratePersistAndReadRunsAndLogsAcrossDialects(DatabaseScenario scenario) {
        try (JdbcDatabaseContainer<?> database = scenario.containerFactory().get()) {
            database.start();
            exerciseRepository(scenario.dialect(), database);
        }
    }

    private void exerciseRepository(Gear4jDatabaseDialect dialect, JdbcDatabaseContainer<?> database) {
        // Given
        DataSource dataSource = new DriverManagerBackedDataSource(database.getJdbcUrl(), database.getUsername(),
                database.getPassword());
        JdbcSchemaMigrator.core(dialect).migrate(dataSource);
        DatabaseAssemblyRunRepository repository = DatabaseAssemblyRunRepository.builder()
                .dataSource(dataSource)
                .databaseDialect(dialect)
                .build();
        UUID runId = UUID.randomUUID();
        UUID stationLogId = UUID.randomUUID();
        Instant startedAt = Instant.now();

        AssemblyRunRecord run = new AssemblyRunRecord(runId, "assembly-line", Map.of("ctx", "value"),
                Map.of("input", "value"), null, ExecutionStatus.RUNNING, startedAt, null, null, null, runId, null);
        StationLogRecord stationLog = new StationLogRecord(stationLogId, runId, "step", null,
                StationLogStatus.SUCCEEDED, startedAt, startedAt.plusMillis(10), null, null,
                Map.of("station", "context"), "item-1");

        // When
        repository.save(run);
        repository.update(new AssemblyRunRecord(runId, "assembly-line", Map.of("ctx", "updated"),
                Map.of("input", "value"), Map.of("result", "ok"), ExecutionStatus.SUCCEEDED, startedAt,
                startedAt.plusMillis(20), null, null, runId, null));
        repository.saveOperationRecord(stationLog);

        // Then
        assertThat(repository.findById(runId)).get().satisfies(savedRun -> {
            assertThat(savedRun.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(savedRun.context()).containsEntry("ctx", "updated");
        });
        assertThat(repository.findAllLogsByRunId(runId, PageRequest.first(10)))
                .singleElement()
                .satisfies(savedLog -> {
                    assertThat(savedLog.operationId()).isEqualTo("step");
                    assertThat(savedLog.context()).containsEntry("station", "context");
                });
    }

    private static Stream<Arguments> databases() {
        String selectedDialect = System.getProperty("gear4j.test.databaseDialect", "all")
                .trim().toLowerCase(Locale.ROOT);
        List<DatabaseScenario> scenarios = List.of(
                                                   new DatabaseScenario("postgresql", Gear4jDatabaseDialect.POSTGRESQL,
                                                           () -> new PostgreSQLContainer<>("postgres:16-alpine")
                                                                   .withDatabaseName("gear4jtest")
                                                                   .withUsername("gear4jtest")
                                                                   .withPassword("gear4jtest")),
                                                   new DatabaseScenario("mysql", Gear4jDatabaseDialect.MYSQL,
                                                           () -> new MySQLContainer<>("mysql:8.4")
                                                                   .withDatabaseName("gear4jtest")
                                                                   .withUsername("gear4jtest")
                                                                   .withPassword("gear4jtest")),
                                                   new DatabaseScenario("mariadb", Gear4jDatabaseDialect.MARIADB,
                                                           () -> new MariaDBContainer<>("mariadb:11.4")
                                                                   .withDatabaseName("gear4jtest")
                                                                   .withUsername("gear4jtest")
                                                                   .withPassword("gear4jtest")),
                                                   new DatabaseScenario("oracle", Gear4jDatabaseDialect.ORACLE,
                                                           () -> new OracleContainer(
                                                                   "gvenzl/oracle-xe:21-slim-faststart")
                                                                   .withUsername("gear4jtest")
                                                                   .withPassword("gear4jtest")));

        if (!"all".equals(selectedDialect)
                && scenarios.stream().noneMatch(scenario -> scenario.id().equals(selectedDialect))) {
            throw new IllegalArgumentException("Unsupported gear4j.test.databaseDialect: " + selectedDialect);
        }
        return scenarios.stream()
                .filter(scenario -> "all".equals(selectedDialect) || scenario.id().equals(selectedDialect))
                .map(Arguments::of);
    }

    private record DatabaseScenario(String id,
                                    Gear4jDatabaseDialect dialect,
                                    Supplier<JdbcDatabaseContainer<?>> containerFactory) {
        @Override
        public String toString() {
            return id;
        }
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
