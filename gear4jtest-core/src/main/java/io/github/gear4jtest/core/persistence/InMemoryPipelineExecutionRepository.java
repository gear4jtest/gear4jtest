package io.github.gear4jtest.core.persistence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryPipelineExecutionRepository implements PipelineExecutionRepository {

    public static final InMemoryPipelineExecutionRepository INSTANCE = new InMemoryPipelineExecutionRepository();

    private final Map<UUID, PipelineExecution> executions = new ConcurrentHashMap<>();

    private InMemoryPipelineExecutionRepository() {
    }

    @Override
    public void save(PipelineExecution execution) {
        executions.put(execution.getId(), execution);
    }

    @Override
    public void update(PipelineExecution execution) {
        executions.put(execution.getId(), execution);
    }

    @Override
    public Optional<PipelineExecution> findById(UUID id) {
        return Optional.ofNullable(executions.get(id));
    }

    @Override
    public List<PipelineExecution> findByPipelineId(String pipelineId) {
        return executions.values().stream()
                .filter(e -> pipelineId.equals(e.getPipelineId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<PipelineExecution> findByStatus(ExecutionStatus status) {
        return executions.values().stream()
                .filter(e -> status.equals(e.getStatus()))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(UUID id) {
        executions.remove(id);
    }

    @Override
    public List<PipelineExecution> findAll() {
        return executions.values().stream().toList();
    }
}