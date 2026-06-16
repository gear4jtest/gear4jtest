package io.github.gear4jtest.core.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class InMemoryAssemblyRunRepository implements AssemblyRunRepository {
    /**
     * Shared mutable repository kept for source compatibility. Prefer creating a
     * dedicated repository instance per engine, test or scenario.
     */
    @Deprecated(since = "0.1.0", forRemoval = true)
    public static final InMemoryAssemblyRunRepository INSTANCE = new InMemoryAssemblyRunRepository();
    private final Map<UUID, AssemblyRunRecord> executions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, StationLogRecord>> stationLogsByRunId = new ConcurrentHashMap<>();

    public InMemoryAssemblyRunRepository() {
    }

    public void clear() {
        executions.clear();
        stationLogsByRunId.clear();
    }

    @Override
    public void save(AssemblyRunRecord execution) {
        executions.put(execution.id(), execution);
    }

    @Override
    public void update(AssemblyRunRecord execution) {
        executions.put(execution.id(), execution);
    }

    @Override
    public Optional<AssemblyRunRecord> findById(UUID id) {
        return Optional.ofNullable(executions.get(id));
    }

    @Override
    public List<AssemblyRunRecord> findByPipelineId(String pipelineId, PageRequest pageRequest) {
        return window(executions.values().stream()
                .filter(e -> pipelineId.equals(e.pipelineId())), pageRequest);
    }

    @Override
    public List<AssemblyRunRecord> findByStatus(ExecutionStatus status, PageRequest pageRequest) {
        return window(executions.values().stream()
                .filter(e -> status.equals(e.status())), pageRequest);
    }

    @Override
    public void delete(UUID id) {
        executions.remove(id);
        stationLogsByRunId.remove(id);
    }

    @Override
    public List<AssemblyRunRecord> findAll(PageRequest pageRequest) {
        return window(executions.values().stream(), pageRequest);
    }

    @Override
    public List<StationLogRecord> findRootLogsByRunId(UUID runId, PageRequest pageRequest) {
        return window(logsByParent(runId, null), pageRequest);
    }

    @Override
    public List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId, PageRequest pageRequest) {
        return window(logsByParent(runId, parentLogId), pageRequest);
    }

    @Override
    public List<StationLogRecord> findAllLogsByRunId(UUID runId, PageRequest pageRequest) {
        Map<UUID, StationLogRecord> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return List.of();
        }

        return window(byLogId.values().stream().sorted(recordComparator()), pageRequest);
    }

    @Override
    public long countChildLogsByRunId(UUID runId, UUID parentLogId) {
        Map<UUID, StationLogRecord> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return 0L;
        }

        return byLogId.values().stream()
                .filter(record -> Objects.equals(parentLogId, record.parentOperationId()))
                .count();
    }

    public void saveOperationRecord(StationLogRecord record) {
        if (record == null) {
            return;
        }

        stationLogsByRunId.computeIfAbsent(record.pipelineExecutionId(), ignored -> new ConcurrentHashMap<>())
                .put(record.id(), record);
    }

    public void saveOperationRecords(List<StationLogRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        for (StationLogRecord record : records) {
            saveOperationRecord(record);
        }
    }

    private Stream<StationLogRecord> logsByParent(UUID runId, UUID parentLogId) {
        Map<UUID, StationLogRecord> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return Stream.empty();
        }

        return byLogId.values().stream()
                .filter(record -> Objects.equals(parentLogId, record.parentOperationId()))
                .sorted(recordComparator());
    }

    private Comparator<StationLogRecord> recordComparator() {
        return Comparator.comparing(StationLogRecord::startedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StationLogRecord::id, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static <T> List<T> window(Stream<T> values, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return values.skip(pageRequest.offset()).limit(pageRequest.limit()).toList();
    }
}
