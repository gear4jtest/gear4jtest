package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssemblyRunRepository {
    default void initialize() {

    }
    void save(AssemblyRun execution);
    void update(AssemblyRun execution);
    Optional<AssemblyRun> findById(UUID id);
    List<AssemblyRun> findByPipelineId(String pipelineId);
    List<AssemblyRun> findByStatus(ExecutionStatus status);
    void delete(UUID id);

    List<AssemblyRun> findAll();
}