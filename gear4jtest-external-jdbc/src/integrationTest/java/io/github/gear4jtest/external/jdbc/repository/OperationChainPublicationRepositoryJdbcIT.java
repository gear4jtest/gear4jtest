package io.github.gear4jtest.external.jdbc.repository;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.consistency.ArtifactPublicationReconciler;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.external.api.storage.ArtifactStoreConfigurationFingerprint;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class OperationChainPublicationRepositoryJdbcIT {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @TempDir
    Path tempDirectory;

    @Test
    void publish_shouldCommitObjectAndTagsAndMakeIdenticalRetriesIdempotent() throws Exception {
        // Given
        DataSource dataSource = h2DataSource();
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        insertConfig(dataSource, "line");
        OperationChainObjectRepositoryJdbc repository = repository(dataSource);
        OperationChainObject publication = publication("1.0.0", HASH);

        // When
        repository.publish(publication, List.of("fast", "xml", "fast"));
        repository.publish(publication, List.of("fast", "xml"));

        // Then
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_object")).isEqualTo(1);
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_tag")).isEqualTo(2);
    }

    @Test
    void commit_shouldRollbackObjectWhenTagPersistenceFails() throws Exception {
        // Given
        DataSource dataSource = h2DataSource();
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        insertConfig(dataSource, "line");
        OperationChainObjectRepositoryJdbc repository = repository(dataSource);
        var stage = repository.stage(publication("2.0.0", HASH), List.of("inserted-before-failure"));
        deleteConfig(dataSource, "line");

        // When / Then
        assertThatThrownBy(() -> repository.commit(stage.stageId()))
                .isInstanceOf(OperationChainRepositoryException.class)
                .hasMessageContaining("commit operation-chain stage");
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_object WHERE version='2.0.0'"))
                .isZero();
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_publication_stage")).isEqualTo(1);
    }

    @Test
    void publish_shouldRejectAConflictingRetryWithoutChangingCommittedMetadata() throws Exception {
        // Given
        DataSource dataSource = h2DataSource();
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        insertConfig(dataSource, "line");
        OperationChainObjectRepositoryJdbc repository = repository(dataSource);
        repository.publish(publication("3.0.0", HASH), List.of("stable"));

        // When / Then
        assertThatThrownBy(() -> repository.publish(publication("3.0.0", "f".repeat(64)), List.of("other")))
                .isInstanceOf(OperationChainPublicationConflictException.class)
                .hasMessageContaining("line:3.0.0:TEST")
                .hasMessageContaining("different content or metadata");
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_object WHERE version='3.0.0'"))
                .isEqualTo(1);
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_tag")).isEqualTo(1);
    }

    @Test
    void stage_shouldRemainInvisibleUntilCommitAndAbortCleanly() throws Exception {
        // Given
        DataSource dataSource = h2DataSource();
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        insertConfig(dataSource, "line");
        OperationChainObjectRepositoryJdbc repository = repository(dataSource);
        OperationChainObject publication = publication("4.0.0", "a".repeat(64));

        // When
        var stage = repository.stage(publication, List.of("xml", "staged"));

        // Then
        assertThat(repository.find("line", "4.0.0", ExecutionMode.TEST)).isEmpty();
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_publication_stage")).isEqualTo(1);
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_publication_stage_tag")).isEqualTo(2);
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(stage);

        // When
        repository.commit(stage.stageId());

        // Then
        assertThat(repository.find("line", "4.0.0", ExecutionMode.TEST)).isPresent();
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_publication_stage")).isZero();
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_publication_stage_tag")).isZero();

        // When
        var aborted = repository.stage(publication("5.0.0", "b".repeat(64)), List.of("abort"));
        repository.abort(aborted.stageId());

        // Then
        assertThat(repository.find("line", "5.0.0", ExecutionMode.TEST)).isEmpty();
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_publication_stage")).isZero();
    }

    @Test
    void conditionalAbort_shouldNotRemoveRenewedStage() throws Exception {
        // Given
        DataSource dataSource = h2DataSource();
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        insertConfig(dataSource, "line");
        OperationChainObjectRepositoryJdbc repository = repository(dataSource);
        OperationChainObject publication = publication("6.0.0", "c".repeat(64));
        var stale = repository.stage(publication, List.of("initial"));

        // When
        var renewed = repository.stage(publication, List.of("retry"));

        // Then
        assertThat(renewed.stageId()).isEqualTo(stale.stageId());
        assertThat(renewed.revision()).isGreaterThan(stale.revision());
        assertThat(repository.abortIfUnchanged(stale)).isFalse();
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(renewed);
        assertThat(repository.abortIfUnchanged(renewed)).isTrue();
    }

    @Test
    void stage_shouldKeepLegacyDelimiterCollisionCandidatesSeparate() throws Exception {
        // Given
        DataSource dataSource = h2DataSource();
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        insertConfig(dataSource, "a:b");
        insertConfig(dataSource, "a");
        OperationChainObjectRepositoryJdbc repository = repository(dataSource);
        OperationChainObject first = publication("a:b", "c", "1".repeat(64));
        OperationChainObject second = publication("a", "b:c", "2".repeat(64));

        // When
        var firstStage = repository.stage(first, List.of("first"));
        var secondStage = repository.stage(second, List.of("second"));

        // Then
        assertThat(firstStage.stageId()).isNotEqualTo(secondStage.stageId());
        assertThat(count(dataSource, "SELECT COUNT(*) FROM operation_chain_publication_stage")).isEqualTo(2);
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactlyInAnyOrder(firstStage, secondStage);
    }

    @Test
    void reconcileAfterRestart_shouldResolveBothStageToStoreCrashWindows() throws Exception {
        // Given: durable metadata and artifact storage survive reconstruction of all
        // Gear4J objects.
        DataSource dataSource = h2DataSource();
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.DATABASE,
                Map.of("dialect", "h2"));
        OperationChainConfigRepositoryJdbc beforeCrashConfigs = configRepository(dataSource);
        beforeCrashConfigs.upsert(config);
        OperationChainObjectRepositoryJdbc beforeCrashMetadata = repository(dataSource);
        DatabaseArtifactStore beforeCrashStore = artifactStore(dataSource, "before-crash");
        byte[] presentContent = "stored-before-metadata-commit".getBytes(StandardCharsets.UTF_8);
        byte[] missingContent = "crashed-before-store-write".getBytes(StandardCharsets.UTF_8);
        OperationChainObject present = publication("7.0.0", ArtifactHashes.sha256Hex(presentContent),
                                                   presentContent.length);
        OperationChainObject missing = publication("8.0.0", ArtifactHashes.sha256Hex(missingContent),
                                                   missingContent.length);
        String fingerprint = ArtifactStoreConfigurationFingerprint.from(config);
        var presentStage = beforeCrashMetadata.stage(present, List.of("recovered"), fingerprint);
        var missingStage = beforeCrashMetadata.stage(missing, List.of("aborted"), fingerprint);
        assertThat(beforeCrashStore.put(presentContent)).isEqualTo(present.contentHash());

        // When: the process restarts after the artifact write but before commit. A
        // second stage represents a crash before the external write began.
        OperationChainConfigRepositoryJdbc recoveredConfigs = configRepository(dataSource);
        OperationChainObjectRepositoryJdbc recoveredMetadata = repository(dataSource);
        OperationChainTagRepositoryJdbc recoveredTags = tagRepository(dataSource);
        DatabaseArtifactStore recoveredStore = artifactStore(dataSource, "after-restart");
        ArtifactPublicationReconciler reconciler = new ArtifactPublicationReconciler(recoveredConfigs,
                recoveredMetadata, ignored -> recoveredStore);
        ArtifactPublicationReconciler.Report report = reconciler
                .reconcileStagedBefore(Instant.now().plusSeconds(1));

        // Then: existing content is committed, missing content is abandoned and a
        // repeated recovery pass is idempotent.
        assertThat(report.stagesChecked()).isEqualTo(2);
        assertThat(report.committed()).isEqualTo(1);
        assertThat(report.aborted()).isEqualTo(1);
        assertThat(report.retained()).isZero();
        assertThat(report.fullyReconciled()).isTrue();
        assertThat(recoveredMetadata.find("line", "7.0.0", ExecutionMode.TEST))
                .hasValueSatisfying(saved -> assertThat(saved.contentIdentity()).isEqualTo(present.contentIdentity()));
        assertThat(recoveredTags.listTags("line")).containsExactly("recovered");
        assertThat(recoveredMetadata.find("line", "8.0.0", ExecutionMode.TEST)).isEmpty();
        assertThat(recoveredMetadata.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .doesNotContain(presentStage, missingStage)
                .isEmpty();

        ArtifactPublicationReconciler.Report repeated = reconciler
                .reconcileStagedBefore(Instant.now().plusSeconds(1));
        assertThat(repeated.stagesChecked()).isZero();
        assertThat(repeated.fullyReconciled()).isTrue();
    }

    private static OperationChainObjectRepositoryJdbc repository(DataSource dataSource) {
        return OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
    }

    private static OperationChainObject publication(String version, String hash) {
        return publication("line", version, hash);
    }

    private static OperationChainObject publication(String version, String hash, long sizeBytes) {
        Instant now = Instant.parse("2026-07-10T10:15:30Z");
        return new OperationChainObject(null, "line", version, ExecutionMode.TEST, hash, sizeBytes,
                "application/xml", now, "tester", now);
    }

    private static OperationChainObject publication(String assemblyLineId, String version, String hash) {
        Instant now = Instant.parse("2026-07-10T10:15:30Z");
        return new OperationChainObject(null, assemblyLineId, version, ExecutionMode.TEST, hash, 42L,
                "application/xml", now, "tester", now);
    }

    private static void insertConfig(DataSource dataSource, String assemblyLineId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                                                            "INSERT INTO operation_chain_config(al_id, allow_run_publication_without_test, "
                                                                    + "store_type, store_props) VALUES (?,?,?,?)")) {
            statement.setString(1, assemblyLineId);
            statement.setBoolean(2, false);
            statement.setString(3, "MEMORY");
            statement.setString(4, "{}");
            statement.executeUpdate();
        }
    }

    private static void deleteConfig(DataSource dataSource, String assemblyLineId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                                                            "DELETE FROM operation_chain_config WHERE al_id=?")) {
            statement.setString(1, assemblyLineId);
            statement.executeUpdate();
        }
    }

    private static OperationChainConfigRepositoryJdbc configRepository(DataSource dataSource) {
        return OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
    }

    private static OperationChainTagRepositoryJdbc tagRepository(DataSource dataSource) {
        return OperationChainTagRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
    }

    private DatabaseArtifactStore artifactStore(DataSource dataSource, String directory) {
        return DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .spoolDirectory(tempDirectory.resolve(directory))
                .build();
    }

    private static int count(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static DataSource h2DataSource() {
        return new DriverManagerBackedDataSource("jdbc:h2:mem:gear4j_publication_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private record DriverManagerBackedDataSource(String url,
                                                 String username,
                                                 String password)
            implements DataSource {
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
