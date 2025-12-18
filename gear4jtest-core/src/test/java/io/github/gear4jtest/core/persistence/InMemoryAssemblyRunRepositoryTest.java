package io.github.gear4jtest.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InMemoryAssemblyRunRepositoryTest {

    @Test
    void saveAndFind_shouldStorePipelineExecution() {
        InMemoryAssemblyRunRepository repo = InMemoryAssemblyRunRepository.INSTANCE;

        UUID id = UUID.randomUUID();
        AssemblyRun exec = new AssemblyRun(id, "pipe", Map.of());

        repo.save(exec);
        Optional<AssemblyRun> res = repo.findById(id);

        assertThat(res).isPresent();
        assertThat(res.get().getPipelineId()).isEqualTo("pipe");
    }

    @Test
    void update_shouldOverrideExistingExecution() {
        InMemoryAssemblyRunRepository repo = InMemoryAssemblyRunRepository.INSTANCE;

        UUID id = UUID.randomUUID();
        AssemblyRun v1 = new AssemblyRun(id, "pipe", Map.of());
        AssemblyRun v2 = new AssemblyRun(id, "pipe2", Map.of());

        repo.save(v1);
        repo.update(v2);

        assertThat(repo.findById(id).get().getPipelineId()).isEqualTo("pipe2");
    }
}
