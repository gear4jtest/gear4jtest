package io.github.gear4jtest.external.api.consistency;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.InMemoryOperationChainRepository;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.storage.ArtifactStoreConfigurationFingerprint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArtifactPublicationReconcilerTest {
    @Test
    void reconcile_shouldBoundOnePassAndContinueWithoutSkippingDeletedStages() throws Exception {
        InMemoryOperationChainRepository repository = new InMemoryOperationChainRepository();
        OperationChainObject first = object("line-1", "1".repeat(64));
        OperationChainObject second = object("line-2", "2".repeat(64));
        OperationChainObject third = object("line-3", "3".repeat(64));
        for (OperationChainObject object : List.of(first, second, third)) {
            repository.stage(object, List.of(), ArtifactStoreConfigurationFingerprint.from(config(object.alId())));
        }
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId(any()))
                .thenAnswer(invocation -> Optional.of(config(invocation.getArgument(0))));
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        ArtifactStore store = mock(ArtifactStore.class);
        when(storeProvider.forConfig(any())).thenReturn(store);
        ArtifactPublicationReconciler reconciler = new ArtifactPublicationReconciler(configRepository, repository,
                storeProvider, 1, 2, 10);
        Instant cutoff = Instant.now().plusSeconds(1);

        ArtifactPublicationReconciler.Report firstPass = reconciler.reconcileStagedBefore(cutoff);
        ArtifactPublicationReconciler.Report secondPass = reconciler.reconcileStagedBefore(cutoff,
                                                                                           firstPass.nextCursor());

        assertThat(firstPass.stagesChecked()).isEqualTo(2);
        assertThat(firstPass.aborted()).isEqualTo(2);
        assertThat(firstPass.complete()).isFalse();
        assertThat(firstPass.nextCursor()).isNotNull();
        assertThat(secondPass.stagesChecked()).isEqualTo(1);
        assertThat(secondPass.aborted()).isEqualTo(1);
        assertThat(secondPass.complete()).isTrue();
        assertThat(secondPass.nextCursor()).isNull();
        assertThat(repository.findStagedBefore(cutoff,
                                               io.github.gear4jtest.core.persistence.PageRequest.first(10)))
                .isEmpty();
    }

    @Test
    void reconcile_shouldCommitStagesWhoseArtifactExistsAndAbortMissingOnes() throws Exception {
        // Given
        InMemoryOperationChainRepository repository = new InMemoryOperationChainRepository();
        OperationChainObject present = object("line-present", "a".repeat(64));
        OperationChainObject missing = object("line-missing", "b".repeat(64));
        OperationChainConfig presentConfig = config("line-present");
        OperationChainConfig missingConfig = config("line-missing");
        repository.stage(present, List.of("committed"),
                         ArtifactStoreConfigurationFingerprint.from(presentConfig));
        repository.stage(missing, List.of("aborted"),
                         ArtifactStoreConfigurationFingerprint.from(missingConfig));
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        ArtifactStore presentStore = mock(ArtifactStore.class);
        ArtifactStore missingStore = mock(ArtifactStore.class);
        when(configRepository.findByAssemblyLineId("line-present")).thenReturn(Optional.of(presentConfig));
        when(configRepository.findByAssemblyLineId("line-missing")).thenReturn(Optional.of(missingConfig));
        when(storeProvider.forConfig(presentConfig)).thenReturn(presentStore);
        when(storeProvider.forConfig(missingConfig)).thenReturn(missingStore);
        when(presentStore.exists(present.contentHash())).thenReturn(true);
        when(missingStore.exists(missing.contentHash())).thenReturn(false);
        ArtifactPublicationReconciler reconciler = new ArtifactPublicationReconciler(configRepository, repository,
                storeProvider, 1);

        // When
        ArtifactPublicationReconciler.Report report = reconciler.reconcileStagedBefore(Instant.now().plusSeconds(1));

        // Then
        assertThat(report.stagesChecked()).isEqualTo(2);
        assertThat(report.committed()).isEqualTo(1);
        assertThat(report.aborted()).isEqualTo(1);
        assertThat(report.retained()).isZero();
        assertThat(report.successful()).isTrue();
        assertThat(report.fullyReconciled()).isTrue();
        assertThat(repository.find("line-present", "1.0.0", ExecutionMode.TEST)).isPresent();
        assertThat(repository.find("line-missing", "1.0.0", ExecutionMode.TEST)).isEmpty();
    }

    @Test
    void reconcile_shouldKeepStageWhenStoreLookupFails() throws Exception {
        // Given
        InMemoryOperationChainRepository repository = new InMemoryOperationChainRepository();
        OperationChainObject object = object("line", "c".repeat(64));
        OperationChainConfig config = config("line");
        var stage = repository.stage(object, List.of("retry"),
                                     ArtifactStoreConfigurationFingerprint.from(config));
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        ArtifactStore store = mock(ArtifactStore.class);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        when(storeProvider.forConfig(config)).thenReturn(store);
        when(store.exists(object.contentHash())).thenThrow(new IOException("store unavailable"));
        ArtifactPublicationReconciler reconciler = new ArtifactPublicationReconciler(configRepository, repository,
                storeProvider);

        // When
        ArtifactPublicationReconciler.Report report = reconciler.reconcileStagedBefore(Instant.now().plusSeconds(1));

        // Then
        assertThat(report.successful()).isFalse();
        assertThat(report.retained()).isEqualTo(1);
        assertThat(report.fullyReconciled()).isFalse();
        assertThat(report.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.message()).contains("store unavailable"));
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1),
                                               io.github.gear4jtest.core.persistence.PageRequest.first(10)))
                .extracting(io.github.gear4jtest.external.api.repository.OperationChainPublicationStage::stageId)
                .containsExactly(stage.stageId());
    }

    @Test
    void reconcile_shouldNotAbortStageRenewedByAnActiveRetry() throws Exception {
        // Given
        InMemoryOperationChainRepository repository = new InMemoryOperationChainRepository();
        OperationChainObject object = object("line", "d".repeat(64));
        OperationChainConfig config = config("line");
        String fingerprint = ArtifactStoreConfigurationFingerprint.from(config);
        var stale = repository.stage(object, List.of("initial"), fingerprint);
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        ArtifactStore store = mock(ArtifactStore.class);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        when(storeProvider.forConfig(config)).thenReturn(store);
        when(store.exists(object.contentHash())).thenAnswer(ignored -> {
            repository.stage(object, List.of("retry"), fingerprint);
            return false;
        });
        ArtifactPublicationReconciler reconciler = new ArtifactPublicationReconciler(configRepository, repository,
                storeProvider);

        // When
        ArtifactPublicationReconciler.Report report = reconciler.reconcileStagedBefore(Instant.now().plusSeconds(1));

        // Then
        assertThat(report.stagesChecked()).isEqualTo(1);
        assertThat(report.aborted()).isZero();
        assertThat(report.retained()).isEqualTo(1);
        assertThat(report.successful()).isTrue();
        assertThat(report.fullyReconciled()).isFalse();
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1),
                                               io.github.gear4jtest.core.persistence.PageRequest.first(10)))
                .singleElement()
                .satisfies(renewed -> {
                    assertThat(renewed.stageId()).isEqualTo(stale.stageId());
                    assertThat(renewed.revision()).isGreaterThan(stale.revision());
                    assertThat(renewed.tags()).containsExactly("initial", "retry");
                });
    }

    @Test
    void reconcile_shouldRetainStageWhenStoreConfigurationChanged() {
        // Given
        InMemoryOperationChainRepository repository = new InMemoryOperationChainRepository();
        OperationChainObject object = object("line", "e".repeat(64));
        OperationChainConfig original = config("line");
        OperationChainConfig changed = new OperationChainConfig("line", false, StoreType.FILESYSTEM,
                Map.of("root", "/new-store"));
        var stage = repository.stage(object, List.of("retry"),
                                     ArtifactStoreConfigurationFingerprint.from(original));
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(changed));
        ArtifactPublicationReconciler reconciler = new ArtifactPublicationReconciler(configRepository, repository,
                storeProvider);

        // When
        ArtifactPublicationReconciler.Report report = reconciler.reconcileStagedBefore(Instant.now().plusSeconds(1));

        // Then
        assertThat(report.successful()).isFalse();
        assertThat(report.retained()).isEqualTo(1);
        assertThat(report.fullyReconciled()).isFalse();
        assertThat(report.failures()).singleElement()
                .satisfies(failure -> assertThat(failure.message()).contains("configuration changed"));
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1),
                                               io.github.gear4jtest.core.persistence.PageRequest.first(10)))
                .containsExactly(stage);
        verifyNoInteractions(storeProvider);
    }

    private static OperationChainObject object(String assemblyLineId, String hash) {
        Instant now = Instant.parse("2026-07-16T12:00:00Z");
        return new OperationChainObject(null, assemblyLineId, "1.0.0", ExecutionMode.TEST, hash, 42L,
                "application/xml", now, "tester", now);
    }

    private static OperationChainConfig config(String assemblyLineId) {
        return new OperationChainConfig(assemblyLineId, false, StoreType.MEMORY, Map.of());
    }
}
