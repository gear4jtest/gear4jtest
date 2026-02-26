package io.github.gear4jtest.core.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.NoOpAssemblyRunManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.sidecompute.SideComputeContext;

public class ExecutionContext {

    private final ThreadLocal<String> currentItemId = new ThreadLocal<>();
    private final ThreadLocal<Deque<UUID>> parentStack = ThreadLocal.withInitial(ArrayDeque::new);

    private final UUID executionId;
    private final String pipelineId;
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private final EventManager eventManager;
    private final AssemblyRunManager assemblyRunManager;
    private final ResourceFactory resourceFactory;
    private final SideComputeContext sideComputeContext = new SideComputeContext();
    private final Map<String, Object> stationScopedResources = new ConcurrentHashMap<>();
    private final AssemblyRun assemblyRun;

    public ExecutionContext(UUID executionId,
                            String pipelineId,
                            EventManager eventManager,
                            ResourceFactory resourceFactory,
                            AssemblyRun assemblyRun) {
        this.pipelineId = pipelineId;
        this.executionId = executionId;
        this.eventManager = eventManager;
        this.resourceFactory = resourceFactory;
        this.assemblyRun = assemblyRun;
        this.assemblyRunManager = NoOpAssemblyRunManager.NO_OP_INSTANCE;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public <T> void put(String key, T value) {
        context.put(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        return type.cast(context.get(key));
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public AssemblyRunManager getAssemblyRunManager() {
        return assemblyRunManager;
    }

    public SideComputeContext getSideComputeContext() {
        return sideComputeContext;
    }

    public String getCurrentItemId() {
        return currentItemId.get();
    }

    public void setCurrentItemId(String itemId) {
        if (itemId == null) {
            currentItemId.remove();
        } else {
            currentItemId.set(itemId);
        }
    }

    public AssemblyRun getPipelineExecution() {
        return assemblyRun;
    }

    public UUID getCurrentParentOperationId() {
        Deque<UUID> stack = parentStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public void pushParentOperationId(UUID operationId) {
        parentStack.get().push(operationId);
    }

    public void popParentOperationId() {
        Deque<UUID> stack = parentStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * Cache de ressources \"scopées run\" (une seule instance par station et par type).
     *
     * <p>Cas d'usage : réutiliser une instance d'Operator stateful au sein d'un même run.
     */
    public <T> T getOrCreateStationResource(String stationId, Class<T> type, Supplier<T> factory) {
        String key = stationId + ":" + type.getName();
        Object value = stationScopedResources.computeIfAbsent(key, k -> factory.get());
        return type.cast(value);
    }
}
