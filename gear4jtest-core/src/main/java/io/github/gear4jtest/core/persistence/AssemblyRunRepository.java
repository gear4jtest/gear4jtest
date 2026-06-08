package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for persisted pipeline runs and their station logs.
 *
 * <p>
 * Implementations may be called by the runtime persistence extension while runs
 * are executing and by application code for read/navigation use cases. Database
 * implementations should provide real bounded pagination for every method that
 * accepts a {@link PageRequest}; these methods must not silently load the full
 * history into memory.
 * </p>
 *
 * <p>
 * Implementations should be thread-safe if they are shared between concurrent
 * pipeline runs. Failures should be reported with a persistence-specific
 * runtime exception containing the operation context when possible.
 * </p>
 */
public interface AssemblyRunRepository {
    /**
     * Initializes the backing store if the implementation owns schema creation or
     * migration. Implementations that rely on an application-owned migrator may
     * keep this as a no-op.
     */
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
     * Helper intended for diagnostics and tests. Production navigation should
     * prefer paged root/child traversal for large runs.
     */
    List<StationLogRecord> findAllLogsByRunId(UUID runId);

    default Optional<AssemblyRunView> findViewById(UUID runId) {
        return findById(runId).map(run -> new AssemblyRunView(run, findRootLogsByRunId(runId)));
    }

}
