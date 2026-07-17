package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RepositoryDefaultPaginationTest {
    @Test
    void operationChainObjectRepositoryDefaultPagination_shouldPageInMemoryResults() {
        OperationChainObject first = object(1L);
        OperationChainObject second = object(2L);
        OperationChainObject third = object(3L);
        OperationChainObjectRepository repository = new InMemoryObjectRepository(List.of(first, second, third));

        assertThat(repository.findAll("pipeline", new PageRequest(1, 1))).containsExactly(second);
        assertThatNullPointerException().isThrownBy(() -> repository.findAll("pipeline", null));
    }

    @Test
    void operationChainTagRepositoryDefaultPagination_shouldPageInMemoryResults() {
        OperationChainTagRepository repository = new InMemoryTagRepository(
                new LinkedHashSet<>(List.of("alpha", "beta", "gamma")), List.of("p1", "p2", "p3"));

        assertThat(repository.listTags("pipeline", new PageRequest(1, 2))).containsExactly("beta", "gamma");
        assertThat(repository.findAssemblyLineIdsByTag("stable", new PageRequest(0, 2))).containsExactly("p1", "p2");
        assertThatNullPointerException().isThrownBy(() -> repository.listTags("pipeline", null));
        assertThatNullPointerException().isThrownBy(() -> repository.findAssemblyLineIdsByTag("stable", null));
    }

    private static OperationChainObject object(Long id) {
        String contentHash = Long.toHexString(id).repeat(64).substring(0, 64);
        return new OperationChainObject(id, "pipeline", "v" + id, ExecutionMode.TEST,
                contentHash, 10L, "application/xml", Instant.EPOCH, "test", Instant.EPOCH);
    }

    private record InMemoryObjectRepository(List<OperationChainObject> objects)
            implements OperationChainObjectRepository {
        @Override
        public long insert(OperationChainObject obj) {
            return obj.id();
        }

        @Override
        public Optional<OperationChainObject> find(String assemblyLineId, String version, ExecutionMode mode) {
            return Optional.empty();
        }

        @Override
        public Optional<OperationChainObject> findLatestRun(String assemblyLineId) {
            return Optional.empty();
        }

        @Override
        public boolean exists(String assemblyLineId, String version, ExecutionMode mode) {
            return false;
        }

        @Override
        public List<OperationChainObject> findAll(String assemblyLineId) {
            return objects;
        }
    }

    private record InMemoryTagRepository(Set<String> tags, List<String> ids) implements OperationChainTagRepository {
        @Override
        public void addTag(String alId, String tag) {
            // No-op: this fixture only verifies default pagination helpers.
        }

        @Override
        public void removeTag(String alId, String tag) {
            // No-op: this fixture only verifies default pagination helpers.
        }

        @Override
        public Set<String> listTags(String alId) {
            return tags;
        }

        @Override
        public List<String> findAssemblyLineIdsByTag(String tag) {
            return ids;
        }
    }
}
