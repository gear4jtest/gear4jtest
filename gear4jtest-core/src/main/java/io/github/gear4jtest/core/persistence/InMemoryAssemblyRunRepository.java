package io.github.gear4jtest.core.persistence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAssemblyRunRepository implements AssemblyRunRepository {

    public static final InMemoryAssemblyRunRepository INSTANCE = new InMemoryAssemblyRunRepository();

    private final Map<UUID, AssemblyRun> executions = new ConcurrentHashMap<>();

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
    }

    @Override
    public List<AssemblyRun> findAll() {
        return executions.values().stream().toList();
    }
}