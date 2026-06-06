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

    List<AssemblyRunRecord> findByPipelineId(String pipelineId, PageRequest pageRequest);

    List<AssemblyRunRecord> findByStatus(ExecutionStatus status);

    List<AssemblyRunRecord> findByStatus(ExecutionStatus status, PageRequest pageRequest);

    void delete(UUID id);

    List<AssemblyRunRecord> findAll();

    List<AssemblyRunRecord> findAll(PageRequest pageRequest);

    List<StationLogRecord> findRootLogsByRunId(UUID runId);

    List<StationLogRecord> findRootLogsByRunId(UUID runId, PageRequest pageRequest);

    List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId);

    List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId, PageRequest pageRequest);

    long countChildLogsByRunId(UUID runId, UUID parentLogId);

    /**
     * Helper debug/test uniquement.
     */
    List<StationLogRecord> findAllLogsByRunId(UUID runId);

    default Optional<AssemblyRunView> findViewById(UUID runId) {
        return findById(runId).map(run -> new AssemblyRunView(run, findRootLogsByRunId(runId)));
    }

}
