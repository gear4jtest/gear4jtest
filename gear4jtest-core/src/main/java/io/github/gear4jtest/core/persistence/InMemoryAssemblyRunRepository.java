package io.github.gear4jtest.core.persistence;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAssemblyRunRepository implements AssemblyRunRepository {
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
    public List<AssemblyRunRecord> findByPipelineId(String pipelineId) {
        return executions.values().stream()
                .filter(e -> pipelineId.equals(e.pipelineId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AssemblyRunRecord> findByPipelineId(String pipelineId, PageRequest pageRequest) {
        return window(findByPipelineId(pipelineId), pageRequest);
    }

    @Override
    public List<AssemblyRunRecord> findByStatus(ExecutionStatus status) {
        return executions.values().stream()
                .filter(e -> status.equals(e.status()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AssemblyRunRecord> findByStatus(ExecutionStatus status, PageRequest pageRequest) {
        return window(findByStatus(status), pageRequest);
    }

    @Override
    public void delete(UUID id) {
        executions.remove(id);
        stationLogsByRunId.remove(id);
    }

    @Override
    public List<AssemblyRunRecord> findAll() {
        return executions.values().stream()
                .toList();
    }

    @Override
    public List<AssemblyRunRecord> findAll(PageRequest pageRequest) {
        return window(findAll(), pageRequest);
    }

    @Override
    public List<StationLogRecord> findRootLogsByRunId(UUID runId) {
        return findLogsByParent(runId, null);
    }

    @Override
    public List<StationLogRecord> findRootLogsByRunId(UUID runId, PageRequest pageRequest) {
        return window(findRootLogsByRunId(runId), pageRequest);
    }

    @Override
    public List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId) {
        return findLogsByParent(runId, parentLogId);
    }

    @Override
    public List<StationLogRecord> findChildLogsByRunId(UUID runId, UUID parentLogId, PageRequest pageRequest) {
        return window(findChildLogsByRunId(runId, parentLogId), pageRequest);
    }

    @Override
    public long countChildLogsByRunId(UUID runId, UUID parentLogId) {
        Map<UUID, StationLogRecord> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return 0L;
        }

        return byLogId.values().stream()
                .filter(record -> java.util.Objects.equals(parentLogId, record.parentOperationId()))
                .count();
    }

    @Override
    public List<StationLogRecord> findAllLogsByRunId(UUID runId) {
        Map<UUID, StationLogRecord> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return List.of();
        }

        return byLogId.values().stream()
                .sorted(recordComparator())
                .toList();
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

    private List<StationLogRecord> findLogsByParent(UUID runId, UUID parentLogId) {
        Map<UUID, StationLogRecord> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return List.of();
        }

        return byLogId.values().stream()
                .filter(record -> {
                    if (parentLogId == null) {
                        return record.parentOperationId() == null;
                    }
                    return parentLogId.equals(record.parentOperationId());
                })
                .sorted(recordComparator())
                .toList();
    }

    private Comparator<StationLogRecord> recordComparator() {
        return Comparator.comparing(StationLogRecord::startedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StationLogRecord::id, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static <T> List<T> window(List<T> values, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        return values.stream().skip(pageRequest.offset()).limit(pageRequest.limit()).toList();
    }
}
