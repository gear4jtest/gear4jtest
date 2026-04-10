package io.github.gear4jtest.core.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAssemblyRunRepository implements AssemblyRunRepository {

    public static final InMemoryAssemblyRunRepository INSTANCE = new InMemoryAssemblyRunRepository();

    private final Map<UUID, AssemblyRun> executions = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, StationLogSnapshot>> stationLogsByRunId = new ConcurrentHashMap<>();

    private InMemoryAssemblyRunRepository() {
    }

    @Override
    public void save(AssemblyRun execution) {
        executions.put(execution.getId(), execution);
    }

    @Override
    public void update(AssemblyRun execution) {
        executions.put(execution.getId(), execution);
    }

    @Override
    public Optional<AssemblyRun> findById(UUID id) {
        return Optional.ofNullable(executions.get(id));
    }

    @Override
    public List<AssemblyRun> findByPipelineId(String pipelineId) {
        return executions.values().stream()
                .filter(e -> pipelineId.equals(e.getPipelineId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AssemblyRun> findByStatus(ExecutionStatus status) {
        return executions.values().stream()
                .filter(e -> status.equals(e.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(UUID id) {
        executions.remove(id);
        stationLogsByRunId.remove(id);
    }

    @Override
    public List<AssemblyRun> findAll() {
        return executions.values().stream().toList();
    }

    @Override
    public List<StationLog> findRootLogsByRunId(UUID runId) {
        return findLogsByParent(runId, null);
    }

    @Override
    public List<StationLog> findChildLogsByRunId(UUID runId, UUID parentLogId) {
        return findLogsByParent(runId, parentLogId);
    }

    @Override
    public long countChildLogsByRunId(UUID runId, UUID parentLogId) {
        Map<UUID, StationLogSnapshot> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return 0L;
        }

        return byLogId.values().stream()
                .filter(snapshot -> parentLogId.equals(snapshot.parentOperationId()))
                .count();
    }

    @Override
    public List<StationLog> findAllLogsByRunId(UUID runId) {
        Map<UUID, StationLogSnapshot> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return List.of();
        }

        return byLogId.values().stream()
                .sorted(snapshotComparator())
                .map(StationLogSnapshot::toStationLog)
                .map(this::detachChildren)
                .toList();
    }

    public void saveOperationSnapshot(StationLogSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        stationLogsByRunId
                .computeIfAbsent(snapshot.pipelineExecutionId(), ignored -> new ConcurrentHashMap<>())
                .put(snapshot.id(), snapshot);
    }

    public void saveOperationSnapshots(List<StationLogSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }

        for (StationLogSnapshot snapshot : snapshots) {
            saveOperationSnapshot(snapshot);
        }
    }

    private List<StationLog> findLogsByParent(UUID runId, UUID parentLogId) {
        Map<UUID, StationLogSnapshot> byLogId = stationLogsByRunId.get(runId);
        if (byLogId == null || byLogId.isEmpty()) {
            return List.of();
        }

        return byLogId.values().stream()
                .filter(snapshot -> {
                    if (parentLogId == null) {
                        return snapshot.parentOperationId() == null;
                    }
                    return parentLogId.equals(snapshot.parentOperationId());
                })
                .sorted(snapshotComparator())
                .map(StationLogSnapshot::toStationLog)
                .map(this::detachChildren)
                .toList();
    }

    private StationLog detachChildren(StationLog log) {
        log.setSubOperations(List.of());
        return log;
    }

    private Comparator<StationLogSnapshot> snapshotComparator() {
        return Comparator
                .comparing(StationLogSnapshot::startedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StationLogSnapshot::id, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
