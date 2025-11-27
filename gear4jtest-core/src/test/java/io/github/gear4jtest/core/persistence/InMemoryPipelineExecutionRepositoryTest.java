package io.github.gear4jtest.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class InMemoryPipelineExecutionRepositoryTest {

    @Test
    void saveAndFind_shouldStorePipelineExecution() {
        InMemoryPipelineExecutionRepository repo = InMemoryPipelineExecutionRepository.INSTANCE;

        UUID id = UUID.randomUUID();
        PipelineExecution exec = new PipelineExecution(id, "pipe", Map.of());

        repo.save(exec);
        Optional<PipelineExecution> res = repo.findById(id);

        assertThat(res).isPresent();
        assertThat(res.get().getPipelineId()).isEqualTo("pipe");
    }

    @Test
    void update_shouldOverrideExistingExecution() {
        InMemoryPipelineExecutionRepository repo = InMemoryPipelineExecutionRepository.INSTANCE;

        UUID id = UUID.randomUUID();
        PipelineExecution v1 = new PipelineExecution(id, "pipe", Map.of());
        PipelineExecution v2 = new PipelineExecution(id, "pipe2", Map.of());

        repo.save(v1);
        repo.update(v2);

        assertThat(repo.findById(id).get().getPipelineId()).isEqualTo("pipe2");
    }
}
