package io.github.gear4jtest.external.api.repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.core.persistence.PageRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RepositoryDefaultPaginationTest {
    @Test
    void operationChainTagRepositoryDefaultPagination_shouldPageInMemoryResults() {
        OperationChainTagRepository repository = new InMemoryTagRepository(
                new LinkedHashSet<>(List.of("alpha", "beta", "gamma")), List.of("p1", "p2", "p3"));

        assertThat(repository.listTags("pipeline", new PageRequest(1, 2))).containsExactly("beta", "gamma");
        assertThat(repository.findAssemblyLineIdsByTag("stable", new PageRequest(0, 2))).containsExactly("p1", "p2");
        assertThatNullPointerException().isThrownBy(() -> repository.listTags("pipeline", null));
        assertThatNullPointerException().isThrownBy(() -> repository.findAssemblyLineIdsByTag("stable", null));
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
