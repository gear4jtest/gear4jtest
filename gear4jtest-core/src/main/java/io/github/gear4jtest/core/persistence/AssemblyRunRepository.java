package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssemblyRunRepository {
    default void initialize() {
    }

    void save(AssemblyRunRecord execution);

    void update(AssemblyRunRecord execution);

    Optional<AssemblyRunRecord> findById(UUID id);

    List<AssemblyRunRecord> findByPipelineId(String pipelineId);

    List<AssemblyRunRecord> findByStatus(ExecutionStatus status);

    void delete(UUID id);

    List<AssemblyRunRecord> findAll();

    List<StationLogRecord> findRootLogsByRunId(UUID runId);

    List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId);

    long countChildLogsByRunId(UUID runId, UUID parentLogId);

    /**
     * Helper debug/test uniquement.
     */
    List<StationLogRecord> findAllLogsByRunId(UUID runId);

    default Optional<AssemblyRunView> findViewById(UUID runId) {
        return findById(runId).map(run -> new AssemblyRunView(run, findRootLogsByRunId(runId)));
    }
}
