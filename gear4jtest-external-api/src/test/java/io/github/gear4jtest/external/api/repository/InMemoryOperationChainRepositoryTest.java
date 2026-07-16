package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
import java.util.List;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(repository.findAll("line"))
                .extracting(OperationChainObject::version)
                .containsExactly("3.0.0", "2.0.0", "1.0.0");
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
