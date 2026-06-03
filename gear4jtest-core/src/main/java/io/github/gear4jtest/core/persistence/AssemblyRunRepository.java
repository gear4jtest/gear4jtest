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

    default List<AssemblyRunRecord> findByPipelineId(String pipelineId, PageRequest pageRequest) {
        return window(findByPipelineId(pipelineId), pageRequest);
    }

    List<AssemblyRunRecord> findByStatus(ExecutionStatus status);

    default List<AssemblyRunRecord> findByStatus(ExecutionStatus status, PageRequest pageRequest) {
        return window(findByStatus(status), pageRequest);
    }

    void delete(UUID id);

    List<AssemblyRunRecord> findAll();

    default List<AssemblyRunRecord> findAll(PageRequest pageRequest) {
        return window(findAll(), pageRequest);
    }

    List<StationLogRecord> findRootLogsByRunId(UUID runId);

    default List<StationLogRecord> findRootLogsByRunId(UUID runId, PageRequest pageRequest) {
        return window(findRootLogsByRunId(runId), pageRequest);
    }

    List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId);

    default List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId, PageRequest pageRequest) {
        return window(findChildLogsByRunId(runId, parentLogId), pageRequest);
    }

    long countChildLogsByRunId(UUID runId, UUID parentLogId);

    /**
     * Helper debug/test uniquement.
     */
    List<StationLogRecord> findAllLogsByRunId(UUID runId);

    default Optional<AssemblyRunView> findViewById(UUID runId) {
        return findById(runId).map(run -> new AssemblyRunView(run, findRootLogsByRunId(runId)));
    }

    private static <T> List<T> window(List<T> values, PageRequest pageRequest) {
        java.util.Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return values.stream().skip(pageRequest.offset()).limit(pageRequest.limit()).toList();
    }
}
