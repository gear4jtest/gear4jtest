package io.github.gear4jtest.external.jdbc.repository;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import io.github.gear4jtest.jdbc.migration.SchemaMigrationException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.io.TempDir;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
class ExternalJdbcMultiDialectIT {
    private static final String MODULE_ID = "gear4j-external-api";
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);
    private static final String HASH_C = "c".repeat(64);
    private static final String HASH_D = "d".repeat(64);

    @TempDir
    Path tempDirectory;

    @ParameterizedTest(name = "{0}")
    @MethodSource("databases")
    void externalJdbc_shouldSupportMigrationsPublicationPaginationAndBlobStreaming(DatabaseScenario scenario,
                                                                                   TestReporter testReporter)
            throws Exception {
        try (JdbcDatabaseContainer<?> database = scenario.containerFactory().get()) {
            database.start();
            exerciseExternalJdbc(scenario, database, testReporter);
        }
    }

    private void exerciseExternalJdbc(DatabaseScenario scenario,
                                      JdbcDatabaseContainer<?> database,
                                      TestReporter testReporter)
            throws Exception {
        DataSource dataSource = new DriverManagerBackedDataSource(database.getJdbcUrl(), database.getUsername(),
                database.getPassword());
        ExternalJdbcSchemaMigrator migrator = ExternalJdbcSchemaMigrator.forDialect(scenario.dialect());

        verifyMigrationsAndChecksum(dataSource, migrator);
        var latestRunIndexEvidence = LatestRunIndexPlanVerifier.verify(
                                                                       dataSource,
                                                                       scenario.dialect(),
                                                                       scenario.id());
        testReporter.publishEntry("findLatestRun." + scenario.id(), latestRunIndexEvidence.report());
        String assemblyLineId = "line-" + scenario.id();
        verifyManagedTransactionRollback(dataSource, scenario, assemblyLineId + "-managed-rollback");
        verifyConfigurationRoundTrip(dataSource, scenario.dialect(), assemblyLineId);
        verifyAtomicPublicationTagsAndPagination(dataSource, scenario.dialect(), assemblyLineId);
        verifyArtifactBlobStreaming(dataSource, scenario, assemblyLineId);
    }

    private static void verifyMigrationsAndChecksum(DataSource dataSource, ExternalJdbcSchemaMigrator migrator)
            throws SQLException {
        // Given / When
        migrator.migrate(dataSource);
        migrator.migrate(dataSource);

        // Then
        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableExists(connection, "artifact_store")).isTrue();
            assertThat(tableExists(connection, "operation_chain_config")).isTrue();
            assertThat(tableExists(connection, "operation_chain_object")).isTrue();
            assertThat(tableExists(connection, "operation_chain_publication_stage")).isTrue();
            assertThat(tableExists(connection, "operation_chain_publication_stage_tag")).isTrue();
            assertThat(tableExists(connection, "operation_chain_tag")).isTrue();
            assertThat(tableExists(connection, "gear4j_schema_history")).isTrue();
        }
        String checksum = migrationChecksum(dataSource);
        assertThat(checksum).hasSize(64).matches("[0-9a-f]{64}");

        // When / Then: an altered history checksum must be detected on every dialect.
        updateMigrationChecksum(dataSource, "0".repeat(64));
        assertThatThrownBy(() -> migrator.migrate(dataSource))
                .isInstanceOf(SchemaMigrationException.class)
                .hasMessageContaining("Checksum mismatch")
                .hasMessageContaining(MODULE_ID + ":1");
        updateMigrationChecksum(dataSource, checksum);
        migrator.migrate(dataSource);
    }

    private static void verifyConfigurationRoundTrip(DataSource dataSource,
                                                     Gear4jDatabaseDialect dialect,
                                                     String assemblyLineId) {
        // Given
        OperationChainConfigRepositoryJdbc repository = OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(dialect)
                .build();
        OperationChainConfig initial = new OperationChainConfig(assemblyLineId, false, StoreType.DATABASE,
                Map.of("dialect", dialect.name().toLowerCase(Locale.ROOT)));

        // When
        repository.upsert(initial);
        repository.setAllowRunPublicationWithoutTest(assemblyLineId, true);
        repository.updateStore(assemblyLineId, StoreType.DATABASE, Map.of("updated", "true"));

        // Then
        OperationChainConfig saved = repository.findByAssemblyLineId(assemblyLineId).orElseThrow();
        assertThat(saved.allowRunPublicationWithoutTest()).isTrue();
        assertThat(saved.storeType()).isEqualTo(StoreType.DATABASE);
        assertThat(saved.storeProps()).isEqualTo(Map.of("updated", "true"));
    }

    private void verifyManagedTransactionRollback(DataSource dataSource,
                                                  DatabaseScenario scenario,
                                                  String assemblyLineId)
            throws Exception {
        byte[] content = ("managed-rollback-" + scenario.id()).getBytes(StandardCharsets.UTF_8);
        String hash;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            JdbcTransactionOperations transactions = work -> work.execute(connection);
            OperationChainConfigRepositoryJdbc configurations = OperationChainConfigRepositoryJdbc.builder()
                    .dataSource(dataSource)
                    .databaseDialect(scenario.dialect())
                    .transactionOperations(transactions)
                    .build();
            OperationChainTagRepositoryJdbc tags = OperationChainTagRepositoryJdbc.builder()
                    .dataSource(dataSource)
                    .databaseDialect(scenario.dialect())
                    .transactionOperations(transactions)
                    .build();
            DatabaseArtifactStore artifacts = DatabaseArtifactStore.builder()
                    .dataSource(dataSource)
                    .databaseDialect(scenario.dialect())
                    .transactionOperations(transactions)
                    .spoolDirectory(tempDirectory.resolve(scenario.id()).resolve("managed-rollback"))
                    .build();

            configurations.upsert(new OperationChainConfig(assemblyLineId, false, StoreType.DATABASE,
                    Map.of("dialect", scenario.id())));
            tags.addTag(assemblyLineId, "rolled-back");
            hash = artifacts.put(content);
            connection.rollback();
        }

        OperationChainConfigRepositoryJdbc configurations = OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(scenario.dialect())
                .build();
        OperationChainTagRepositoryJdbc tags = OperationChainTagRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(scenario.dialect())
                .build();
        DatabaseArtifactStore artifacts = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(scenario.dialect())
                .spoolDirectory(tempDirectory.resolve(scenario.id()).resolve("managed-rollback-verification"))
                .build();
        assertThat(configurations.findByAssemblyLineId(assemblyLineId)).isEmpty();
        assertThat(tags.listTags(assemblyLineId)).isEmpty();
        assertThat(artifacts.exists(hash)).isFalse();
    }

    private static void verifyAtomicPublicationTagsAndPagination(DataSource dataSource,
                                                                 Gear4jDatabaseDialect dialect,
                                                                 String assemblyLineId)
            throws SQLException {
        OperationChainObjectRepositoryJdbc objects = OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(dialect)
                .build();
        OperationChainTagRepositoryJdbc tags = OperationChainTagRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(dialect)
                .build();
        Instant baseTime = Instant.parse("2026-07-15T10:15:30Z");
        OperationChainObject testPublication = publication(assemblyLineId, "1.0.0-test", ExecutionMode.TEST,
                                                           HASH_A, baseTime);
        OperationChainObject firstRun = publication(assemblyLineId, "1.0.0", ExecutionMode.RUN, HASH_B,
                                                    baseTime.plusSeconds(1));
        OperationChainObject latestRun = publication(assemblyLineId, "2.0.0", ExecutionMode.RUN, HASH_C,
                                                     baseTime.plusSeconds(2));

        // When
        objects.publish(testPublication, List.of("alpha", "shared", "alpha"));
        objects.publish(testPublication, List.of("alpha", "shared"));
        objects.publish(firstRun, List.of("beta", "shared"));
        objects.publish(latestRun, List.of("gamma", "shared"));

        // Then: idempotency, tags and dialect-specific paging.
        OperationChainObject savedLatestRun = objects.findLatestRun(assemblyLineId).orElseThrow();
        assertThat(savedLatestRun.version()).isEqualTo("2.0.0");
        List<OperationChainObject> objectPage = objects.findAll(assemblyLineId, new PageRequest(1, 1));
        assertThat(objectPage).hasSize(1);
        assertThat(objectPage.get(0).version()).isEqualTo("1.0.0");
        assertThat(tags.listTags(assemblyLineId, new PageRequest(1, 2)))
                .containsExactly("beta", "gamma");
        assertThat(tags.findAssemblyLineIdsByTag("shared", PageRequest.first(10)))
                .containsExactly(assemblyLineId);
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_object WHERE al_id='"
                + assemblyLineId + "'"))
                .isEqualTo(3);
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_tag WHERE al_id='"
                + assemblyLineId + "'"))
                .isEqualTo(4);

        // When / Then: a tag foreign-key failure rolls the object back on every
        // supported database.
        String rollbackAssemblyLineId = assemblyLineId + "-rollback";
        OperationChainConfigRepositoryJdbc configs = OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(dialect)
                .build();
        configs.upsert(new OperationChainConfig(rollbackAssemblyLineId, false, StoreType.MEMORY, Map.of()));
        var rollbackStage = objects.stage(
                                          publication(rollbackAssemblyLineId, "rollback", ExecutionMode.TEST, HASH_D,
                                                      baseTime.plusSeconds(3)),
                                          List.of("inserted-before-failure"));
        deleteConfig(dataSource, rollbackAssemblyLineId);

        assertThatThrownBy(() -> objects.commit(rollbackStage.stageId()))
                .isInstanceOf(OperationChainRepositoryException.class)
                .hasMessageContaining("commit operation-chain stage");
        assertThat(objects.exists(rollbackAssemblyLineId, "rollback", ExecutionMode.TEST)).isFalse();
        assertThat(objects.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(100)))
                .containsExactly(rollbackStage);
        objects.abort(rollbackStage.stageId());

        // When / Then: a conflicting retry must preserve the committed metadata.
        assertThatThrownBy(() -> objects.publish(
                                                 publication(assemblyLineId, "1.0.0", ExecutionMode.RUN, HASH_D,
                                                             baseTime.plusSeconds(1)),
                                                 List.of("other")))
                .isInstanceOf(OperationChainPublicationConflictException.class)
                .hasMessageContaining(assemblyLineId + ":1.0.0:RUN");
        OperationChainObject committed = objects.find(assemblyLineId, "1.0.0", ExecutionMode.RUN).orElseThrow();
        assertThat(committed.contentHash()).isEqualTo(HASH_B);
    }

    private void verifyArtifactBlobStreaming(DataSource dataSource,
                                             DatabaseScenario scenario,
                                             String assemblyLineId)
            throws Exception {
        // Given
        byte[] content = new byte[256 * 1024 + 17];
        for (int index = 0; index < content.length; index++) {
            content[index] = (byte) (index * 31 + assemblyLineId.length());
        }
        DatabaseArtifactStore store = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(scenario.dialect())
                .spoolDirectory(tempDirectory.resolve(scenario.id()))
                .build();

        // When
        String hash = store.put(new ByteArrayInputStream(content), content.length);
        String duplicateHash = store.put(new ByteArrayInputStream(content), content.length);

        // Then
        assertThat(duplicateHash).isEqualTo(hash);
        assertThat(store.exists(hash)).isTrue();
        var artifact = store.get(hash).orElseThrow();
        assertThat(artifact.size()).isEqualTo(content.length);
        try (var input = artifact.openStream()) {
            byte[] prefix = input.readNBytes(257);
            byte[] remainder = input.readAllBytes();
            byte[] reassembled = new byte[prefix.length + remainder.length];
            System.arraycopy(prefix, 0, reassembled, 0, prefix.length);
            System.arraycopy(remainder, 0, reassembled, prefix.length, remainder.length);
            assertThat(reassembled).isEqualTo(content);
        }

        // When / Then: same-size corruption is rejected both on read and on an
        // idempotent retry, independently of the database dialect.
        byte[] corrupt = content.clone();
        corrupt[corrupt.length / 2] ^= 1;
        updateArtifactContent(dataSource, hash, corrupt);
        assertThatThrownBy(() -> {
            try (var input = store.get(hash).orElseThrow().openStreamChecked()) {
                input.readAllBytes();
            }
        }).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("content hash mismatch")
                .hasMessageContaining(hash);
        assertThatThrownBy(() -> store.put(new ByteArrayInputStream(content), content.length))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Failed to persist database artifact")
                .hasRootCauseMessage("Existing artifact content hash mismatch for " + hash + ": expected "
                        + hash + " but found "
                        + ArtifactHashes.sha256Hex(corrupt));

        updateArtifactContent(dataSource, hash, content);
        assertThat(store.put(new ByteArrayInputStream(content), content.length)).isEqualTo(hash);
    }

    private static OperationChainObject publication(String assemblyLineId,
                                                    String version,
                                                    ExecutionMode mode,
                                                    String hash,
                                                    Instant publishedAt) {
        return new OperationChainObject(null, assemblyLineId, version, mode, hash, 42L, "application/xml",
                publishedAt.minusSeconds(1), "phase-7", publishedAt);
    }

    private static void deleteConfig(DataSource dataSource, String assemblyLineId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                                                            "DELETE FROM operation_chain_config WHERE al_id=?")) {
            statement.setString(1, assemblyLineId);
            statement.executeUpdate();
        }
    }

    private static void updateArtifactContent(DataSource dataSource, String hash, byte[] content)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                                                            "UPDATE artifact_store SET content=? WHERE hash_hex=?")) {
            statement.setBinaryStream(1, new ByteArrayInputStream(content), content.length);
            statement.setString(2, hash);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Expected to update exactly one artifact fixture");
            }
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(null, null, null, new String[] { "TABLE" })) {
            while (resultSet.next()) {
                if (tableName.equalsIgnoreCase(resultSet.getString("TABLE_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String migrationChecksum(DataSource dataSource) throws SQLException {
        String sql = "SELECT checksum FROM gear4j_schema_history WHERE module_id=? AND version=?";
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, MODULE_ID);
            statement.setString(2, "1");
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AssertionError("Missing external JDBC migration history row");
                }
                String checksum = resultSet.getString(1);
                if (resultSet.next()) {
                    throw new AssertionError("Duplicate external JDBC migration history row");
                }
                return checksum;
            }
        }
    }

    private static void updateMigrationChecksum(DataSource dataSource, String checksum) throws SQLException {
        String sql = "UPDATE gear4j_schema_history SET checksum=? WHERE module_id=? AND version=?";
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, checksum);
            statement.setString(2, MODULE_ID);
            statement.setString(3, "1");
            int updatedRows = statement.executeUpdate();
            if (updatedRows != 1) {
                throw new AssertionError("Expected one migration history row but updated " + updatedRows);
            }
        }
    }

    private static int count(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                throw new AssertionError("Count query returned no row: " + sql);
            }
            return resultSet.getInt(1);
        }
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
