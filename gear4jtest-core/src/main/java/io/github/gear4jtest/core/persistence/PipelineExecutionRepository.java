package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineExecutionRepository {
    default void initialize() {

    }
    void save(PipelineExecution execution);
    void update(PipelineExecution execution);
    Optional<PipelineExecution> findById(UUID id);
    List<PipelineExecution> findByPipelineId(String pipelineId);
    List<PipelineExecution> findByStatus(ExecutionStatus status);
    void delete(String id);

    List<PipelineExecution> findAll();
}