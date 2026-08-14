package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
import java.util.List;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryOperationChainRepositoryTest {
    private final InMemoryOperationChainRepository repository = new InMemoryOperationChainRepository();

    @Test
    void publish_shouldAtomicallyPersistObjectAndDeduplicatedTags() {
        // Given
        OperationChainObject object = object("line", "1.0.0", ExecutionMode.TEST, "a".repeat(64),
                                             Instant.parse("2026-07-16T10:00:00Z"));

        // When
        repository.publish(object, List.of("xml", "fast", "xml"));
        repository.publish(object, List.of("stable"));

        // Then
        OperationChainObject stored = repository.find("line", "1.0.0", ExecutionMode.TEST).orElseThrow();
        assertThat(stored.id()).isEqualTo(1L);
        assertThat(stored.contentHash()).isEqualTo("a".repeat(64));
        assertThat(repository.listTags("line")).containsExactly("fast", "stable", "xml");
        assertThat(repository.findAssemblyLineIdsByTag("stable")).containsExactly("line");
    }

    @Test
    void directTagOperations_shouldApplyThePublicationTagSchema() {
        assertThatThrownBy(() -> repository.addTag("line", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag must not be blank");
        assertThatThrownBy(() -> repository.removeTag("line", "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag must not exceed 100 characters");
        assertThatThrownBy(() -> repository.findAssemblyLineIdsByTag(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag must not be blank");
    }

    @Test
    void publish_shouldLeaveExistingObjectAndTagsUnchangedOnConflict() {
        // Given
        OperationChainObject existing = object("line", "1.0.0", ExecutionMode.TEST, "a".repeat(64),
                                               Instant.parse("2026-07-16T10:00:00Z"));
        OperationChainObject conflicting = object("line", "1.0.0", ExecutionMode.TEST, "b".repeat(64),
                                                  Instant.parse("2026-07-16T11:00:00Z"));
        repository.publish(existing, List.of("stable"));

        // When / Then
        assertThatThrownBy(() -> repository.publish(conflicting, List.of("should-not-exist")))
                .isInstanceOf(OperationChainPublicationConflictException.class)
                .hasMessageContaining("different content or metadata");
        OperationChainObject stored = repository.find("line", "1.0.0", ExecutionMode.TEST).orElseThrow();
        assertThat(stored.contentHash()).isEqualTo("a".repeat(64));
        assertThat(repository.listTags("line")).containsExactly("stable");
    }

    @Test
    void findLatestRun_shouldUsePublicationTimeThenIdentifier() {
        // Given
        repository.publish(object("line", "1.0.0", ExecutionMode.RUN, "a".repeat(64),
                                  Instant.parse("2026-07-16T10:00:00Z")),
                           List.of());
        repository.publish(object("line", "2.0.0", ExecutionMode.RUN, "b".repeat(64),
                                  Instant.parse("2026-07-16T11:00:00Z")),
                           List.of());
        repository.publish(object("line", "3.0.0", ExecutionMode.TEST, "c".repeat(64),
                                  Instant.parse("2026-07-16T12:00:00Z")),
                           List.of());

        // When / Then
        OperationChainObject latestRun = repository.findLatestRun("line").orElseThrow();
        assertThat(latestRun.version()).isEqualTo("2.0.0");
    }

    @Test
    void findAll_shouldApplyRequiredBoundedPage() {
        // Given
        repository.publish(object("line", "1.0.0", ExecutionMode.RUN, "a".repeat(64),
                                  Instant.parse("2026-07-16T10:00:00Z")),
                           List.of());
        repository.publish(object("line", "2.0.0", ExecutionMode.RUN, "b".repeat(64),
                                  Instant.parse("2026-07-16T11:00:00Z")),
                           List.of());
        repository.publish(object("line", "3.0.0", ExecutionMode.TEST, "c".repeat(64),
                                  Instant.parse("2026-07-16T12:00:00Z")),
                           List.of());

        // When / Then
        assertThat(repository.findAll("line", PageRequest.first(2)))
                .extracting(OperationChainObject::version)
                .containsExactly("3.0.0", "2.0.0");
        assertThat(repository.findAll("line", new PageRequest(1, 1)))
                .extracting(OperationChainObject::version)
                .containsExactly("2.0.0");
        assertThatNullPointerException()
                .isThrownBy(() -> repository.findAll("line", null))
                .withMessage("pageRequest must not be null");
    }

    @Test
    void stagedPublication_shouldRemainInvisibleUntilCommitAndBeRecoverable() {
        // Given
        OperationChainObject object = object("line", "4.0.0", ExecutionMode.TEST, "d".repeat(64),
                                             Instant.parse("2026-07-16T13:00:00Z"));

        // When
        OperationChainPublicationStage stage = repository.stage(object, List.of("xml", "xml", "staged"));

        // Then
        assertThat(repository.find("line", "4.0.0", ExecutionMode.TEST)).isEmpty();
        assertThat(repository.listTags("line")).isEmpty();
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(stage);

        // When
        repository.commit(stage.stageId());

        // Then
        assertThat(repository.find("line", "4.0.0", ExecutionMode.TEST)).isPresent();
        assertThat(repository.listTags("line")).containsExactly("staged", "xml");
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10))).isEmpty();
    }

    @Test
    void stagedPublication_shouldMergeIdempotentTagsAndAbortWithoutVisibility() {
        // Given
        OperationChainObject object = object("line", "5.0.0", ExecutionMode.TEST, "e".repeat(64),
                                             Instant.parse("2026-07-16T14:00:00Z"));

        // When
        OperationChainPublicationStage first = repository.stage(object, List.of("xml"));
        OperationChainPublicationStage second = repository.stage(object, List.of("stable"));

        // Then
        assertThat(second.stageId()).isEqualTo(first.stageId());
        assertThat(second.tags()).containsExactly("stable", "xml");

        // When
        repository.abort(first.stageId());

        // Then
        assertThat(repository.find("line", "5.0.0", ExecutionMode.TEST)).isEmpty();
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10))).isEmpty();
    }

    @Test
    void stagedPublication_shouldKeepLegacyDelimiterCollisionCandidatesSeparate() {
        // Given
        OperationChainObject first = object("a:b", "c", ExecutionMode.TEST, "1".repeat(64), Instant.EPOCH);
        OperationChainObject second = object("a", "b:c", ExecutionMode.TEST, "2".repeat(64), Instant.EPOCH);

        // When
        OperationChainPublicationStage firstStage = repository.stage(first, List.of("first"));
        OperationChainPublicationStage secondStage = repository.stage(second, List.of("second"));

        // Then
        assertThat(firstStage.stageId()).isNotEqualTo(secondStage.stageId());
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactlyInAnyOrder(firstStage, secondStage);
    }

    @Test
    void stagedPublication_shouldRejectConflictingCandidateWithoutChangingStage() {
        // Given
        OperationChainObject existing = object("line", "6.0.0", ExecutionMode.TEST, "f".repeat(64),
                                               Instant.parse("2026-07-16T15:00:00Z"));
        OperationChainObject conflicting = object("line", "6.0.0", ExecutionMode.TEST, "a".repeat(64),
                                                  Instant.parse("2026-07-16T15:01:00Z"));
        OperationChainPublicationStage stage = repository.stage(existing, List.of("stable"));

        // When / Then
        assertThatThrownBy(() -> repository.stage(conflicting, List.of("conflict")))
                .isInstanceOf(OperationChainPublicationConflictException.class);
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(stage);
    }

    @Test
    void stagedPublication_shouldRejectMatchingHashWithDifferentSizeOrMimeType() {
        // Given
        Instant publishedAt = Instant.parse("2026-07-16T15:00:00Z");
        OperationChainObject existing = object("line", "6.1.0", ExecutionMode.TEST, "f".repeat(64), publishedAt);
        OperationChainPublicationStage stage = repository.stage(existing, List.of("stable"));
        OperationChainObject differentSize = new OperationChainObject(null, "line", "6.1.0", ExecutionMode.TEST,
                existing.contentHash(), existing.sizeBytes() + 1L, existing.mimeType(), publishedAt, "tester",
                publishedAt);
        OperationChainObject differentMimeType = new OperationChainObject(null, "line", "6.1.0",
                ExecutionMode.TEST, existing.contentHash(), existing.sizeBytes(), "application/json", publishedAt,
                "tester", publishedAt);

        // When / Then
        assertThatThrownBy(() -> repository.stage(differentSize, List.of("different-size")))
                .isInstanceOf(OperationChainPublicationConflictException.class);
        assertThatThrownBy(() -> repository.stage(differentMimeType, List.of("different-mime")))
                .isInstanceOf(OperationChainPublicationConflictException.class);
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(stage);
    }

    @Test
    void commit_shouldRejectMatchingHashWithDifferentCommittedMetadata() {
        // Given
        Instant publishedAt = Instant.parse("2026-07-16T15:00:00Z");
        OperationChainObject staged = object("line", "6.2.0", ExecutionMode.TEST, "f".repeat(64), publishedAt);
        OperationChainPublicationStage stage = repository.stage(staged, List.of("staged"));
        OperationChainObject conflicting = new OperationChainObject(null, "line", "6.2.0", ExecutionMode.TEST,
                staged.contentHash(), staged.sizeBytes() + 1L, staged.mimeType(), publishedAt, "tester", publishedAt);
        repository.insert(conflicting);

        // When / Then
        assertThatThrownBy(() -> repository.commit(stage.stageId()))
                .isInstanceOf(OperationChainPublicationConflictException.class);
        assertThat(repository.find("line", "6.2.0", ExecutionMode.TEST).orElseThrow().sizeBytes())
                .isEqualTo(conflicting.sizeBytes());
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(stage);
        assertThat(repository.listTags("line")).doesNotContain("staged");
    }

    @Test
    void stagedPublication_shouldRenewGracePeriodAndProtectActiveRetryFromStaleAbort() {
        // Given
        OperationChainObject object = object("line", "7.0.0", ExecutionMode.TEST, "b".repeat(64),
                                             Instant.parse("2026-07-16T16:00:00Z"));
        OperationChainPublicationStage stale = repository.stage(object, List.of("initial"));

        // When
        OperationChainPublicationStage renewed = repository.stage(object, List.of("retry"));

        // Then
        assertThat(renewed.stageId()).isEqualTo(stale.stageId());
        assertThat(renewed.revision()).isEqualTo(stale.revision() + 1L);
        assertThat(renewed.stagedAt()).isAfterOrEqualTo(stale.stagedAt());
        assertThat(repository.abortIfUnchanged(stale)).isFalse();
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(renewed);
        assertThat(repository.abortIfUnchanged(renewed)).isTrue();
    }

    @Test
    void stagedPublication_shouldRejectRetryUsingDifferentStoreConfiguration() {
        // Given
        OperationChainObject object = object("line", "8.0.0", ExecutionMode.TEST, "c".repeat(64),
                                             Instant.parse("2026-07-16T17:00:00Z"));
        String firstFingerprint = "1".repeat(64);
        String secondFingerprint = "2".repeat(64);
        OperationChainPublicationStage stage = repository.stage(object, List.of("initial"), firstFingerprint);

        // When / Then
        assertThatThrownBy(() -> repository.stage(object, List.of("retry"), secondFingerprint))
                .isInstanceOf(OperationChainPublicationConflictException.class);
        assertThat(repository.findStagedBefore(Instant.now().plusSeconds(1), PageRequest.first(10)))
                .containsExactly(stage);
    }

    private static OperationChainObject object(String assemblyLineId,
                                               String version,
                                               ExecutionMode mode,
                                               String contentHash,
                                               Instant publishedAt) {
        return new OperationChainObject(null, assemblyLineId, version, mode, contentHash, 42L, "application/xml",
                publishedAt, "tester", publishedAt);
    }
}
