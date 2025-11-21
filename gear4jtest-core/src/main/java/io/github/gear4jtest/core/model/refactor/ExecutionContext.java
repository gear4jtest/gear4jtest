package io.github.gear4jtest.core.model.refactor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.github.gear4jtest.core.execution.PipelineExecutionManager;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.factory.ResourceFactory;

public class ExecutionContext {
    private final UUID executionId;
    private final String pipelineId;
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private final EventManager eventManager;
    private final PipelineExecutionManager executionManager;
    private final ResourceFactory resourceFactory;

    public ExecutionContext(String pipelineId, EventManager eventManager, ResourceFactory resourceFactory, PipelineExecutionManager executionManager) {
        this.pipelineId = pipelineId;
        this.executionId = UUID.randomUUID();
        this.eventManager = eventManager;
        this.resourceFactory = resourceFactory;
        this.executionManager = executionManager;
    }

    public String getPipelineId() {
        return pipelineId;
    }
    public <T> void put(String key, T value) { context.put(key, value); }
    public <T> T get(String key, Class<T> type) { return type.cast(context.get(key)); }
    public EventManager getEventManager() { return eventManager; }
    public UUID getExecutionId() { return executionId; }
    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }
    public Map<String, Object> getContext() {
        return context;
    }

    public PipelineExecutionManager getExecutionManager() { return executionManager; }
}
