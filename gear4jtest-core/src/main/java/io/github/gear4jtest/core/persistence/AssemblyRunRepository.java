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

    List<StationLog> findRootLogsByRunId(UUID runId);

    List<StationLog> findChildLogsByRunId(UUID runId, UUID parentLogId);

    long countChildLogsByRunId(UUID runId, UUID parentLogId);

    /**
     * Helper debug/test uniquement.
     */
    List<StationLog> findAllLogsByRunId(UUID runId);

    default Optional<AssemblyRunDetails> findDetailsById(UUID runId) {
        return findById(runId).map(run -> new AssemblyRunDetails(run, findRootLogsByRunId(runId)));
    }
}
