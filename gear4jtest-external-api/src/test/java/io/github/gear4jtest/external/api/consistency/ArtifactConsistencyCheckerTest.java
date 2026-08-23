package io.github.gear4jtest.external.api.consistency;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.repository.OperationChainObjectCursor;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactConsistencyCheckerTest {
    private static final String PRESENT_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String MISSING_HASH = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void check_shouldPageObjectsDeduplicateArtifactReadsAndReportIssues() throws Exception {
        // Given
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        OperationChainObjectRepository objectRepository = mock(OperationChainObjectRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        ArtifactStore store = mock(ArtifactStore.class);
        OperationChainConfig config = new OperationChainConfig("line", true, StoreType.DATABASE, Map.of());
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        when(storeProvider.forConfig(config)).thenReturn(store);
        OperationChainObject first = object(3L, "1", ExecutionMode.TEST, PRESENT_HASH, 3);
        OperationChainObject second = object(2L, "2", ExecutionMode.RUN, PRESENT_HASH, 4);
        OperationChainObject third = object(1L, "3", ExecutionMode.TEST, MISSING_HASH, 5);
        when(objectRepository.findAllAfter(eq("line"), isNull(), eq(2))).thenReturn(List.of(first, second));
        when(objectRepository.findAllAfter(eq("line"), eq(OperationChainObjectCursor.after(second)), eq(2)))
                .thenReturn(List.of(third));
        when(store.get(PRESENT_HASH)).thenReturn(Optional.of(new Artifact(PRESENT_HASH, 3, Map.of(), null)));
        when(store.get(MISSING_HASH)).thenReturn(Optional.empty());
        ArtifactConsistencyChecker checker = new ArtifactConsistencyChecker(configRepository, objectRepository,
                storeProvider, 2);

        // When
        ArtifactConsistencyChecker.Report report = checker.check("line");

        // Then
        assertThat(report.consistent()).isFalse();
        assertThat(report.complete()).isTrue();
        assertThat(report.nextCursor()).isNull();
        assertThat(report.objectsChecked()).isEqualTo(3);
        assertThat(report.uniqueArtifactsChecked()).isEqualTo(2);
        assertThat(report.issues())
                .extracting(ArtifactConsistencyChecker.Issue::type,
                            ArtifactConsistencyChecker.Issue::version,
                            ArtifactConsistencyChecker.Issue::expectedSizeBytes,
                            ArtifactConsistencyChecker.Issue::actualSizeBytes)
                .containsExactly(
                                 org.assertj.core.groups.Tuple.tuple(
                                                                     ArtifactConsistencyChecker.Type.SIZE_MISMATCH, "2",
                                                                     4L, 3L),
                                 org.assertj.core.groups.Tuple.tuple(
                                                                     ArtifactConsistencyChecker.Type.MISSING_ARTIFACT,
                                                                     "3", 5L, null));
        verify(store, times(1)).get(PRESENT_HASH);
        verify(store, times(1)).get(MISSING_HASH);
        verify(storeProvider).release(store);
        verify(objectRepository).findAllAfter("line", null, 2);
        verify(objectRepository).findAllAfter("line", OperationChainObjectCursor.after(second), 2);
    }

    @Test
    void check_shouldStopAtBudgetAndExposeAStableContinuationCursor() throws Exception {
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        OperationChainObjectRepository objectRepository = mock(OperationChainObjectRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        ArtifactStore store = mock(ArtifactStore.class);
        OperationChainConfig config = new OperationChainConfig("line", true, StoreType.MEMORY, Map.of());
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        when(storeProvider.forConfig(config)).thenReturn(store);
        OperationChainObject first = object(3L, "1", ExecutionMode.TEST, PRESENT_HASH, 3);
        OperationChainObject second = object(2L, "2", ExecutionMode.RUN, PRESENT_HASH, 4);
        OperationChainObject third = object(1L, "3", ExecutionMode.TEST, MISSING_HASH, 5);
        OperationChainObjectCursor continuation = OperationChainObjectCursor.after(second);
        when(objectRepository.findAllAfter("line", null, 2)).thenReturn(List.of(first, second));
        when(objectRepository.findAllAfter("line", continuation, 1)).thenReturn(List.of(third));
        when(store.get(PRESENT_HASH)).thenReturn(Optional.of(new Artifact(PRESENT_HASH, 3, Map.of(), null)));
        ArtifactConsistencyChecker checker = new ArtifactConsistencyChecker(configRepository, objectRepository,
                storeProvider, 2, 2, 1);

        ArtifactConsistencyChecker.Report report = checker.check("line");

        assertThat(report.objectsChecked()).isEqualTo(2);
        assertThat(report.complete()).isFalse();
        assertThat(report.consistent()).isFalse();
        assertThat(report.nextCursor()).isEqualTo(continuation);
        assertThat(report.issues()).hasSize(1);
        assertThat(report.issuesOmitted()).isZero();
        verify(objectRepository).findAllAfter("line", continuation, 1);
    }

    @Test
    void check_shouldRejectMissingConfigurationAndInvalidPageSize() {
        // Given
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        OperationChainObjectRepository objectRepository = mock(OperationChainObjectRepository.class);
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        when(configRepository.findByAssemblyLineId("missing")).thenReturn(Optional.empty());
        ArtifactConsistencyChecker checker = new ArtifactConsistencyChecker(configRepository, objectRepository,
                storeProvider);

        // When / Then
        assertThatThrownBy(() -> checker.check("missing"))
                .isInstanceOf(OperationChainNotFoundException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> new ArtifactConsistencyChecker(configRepository, objectRepository, storeProvider, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageSize");
    }

    private static OperationChainObject object(long id,
                                               String version,
                                               ExecutionMode mode,
                                               String hash,
                                               long size) {
        Instant publishedAt = Instant.parse("2026-08-22T12:00:00Z").plusSeconds(id);
        return new OperationChainObject(id, "line", version, mode, hash, size, "application/xml", publishedAt,
                "test", publishedAt);
    }
}
