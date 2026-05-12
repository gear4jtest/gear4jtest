package io.github.gear4jtest.core.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAssemblyRunRepositoryTest {
    @Test
    void saveAndFind_shouldStorePipelineExecution() {
        InMemoryAssemblyRunRepository repo = InMemoryAssemblyRunRepository.INSTANCE;

        UUID id = UUID.randomUUID();
        AssemblyRunRecord exec = AssemblyRunRecord.from(new AssemblyRunTrace(id, "pipe", Map.of()));

        repo.save(exec);
        Optional<AssemblyRunRecord> res = repo.findById(id);

        assertThat(res).isPresent();
        assertThat(res.get().pipelineId()).isEqualTo("pipe");
    }

    @Test
    void update_shouldOverrideExistingExecution() {
        InMemoryAssemblyRunRepository repo = InMemoryAssemblyRunRepository.INSTANCE;

        UUID id = UUID.randomUUID();
        AssemblyRunRecord v1 = AssemblyRunRecord.from(new AssemblyRunTrace(id, "pipe", Map.of()));
        AssemblyRunRecord v2 = AssemblyRunRecord.from(new AssemblyRunTrace(id, "pipe2", Map.of()));

        repo.save(v1);
        repo.update(v2);

        assertThat(repo.findById(id).get().pipelineId()).isEqualTo("pipe2");
    }
}
