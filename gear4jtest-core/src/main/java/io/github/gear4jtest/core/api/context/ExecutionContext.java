package io.github.gear4jtest.core.api.context;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.sidecompute.SideComputeContext;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ExecutionContext {

    public static final class EventRuntimeOptions {

        private final boolean parameterResolvedEventsEnabled;
        private final EventPayloadPolicy eventPayloadPolicy;

        private EventRuntimeOptions(boolean parameterResolvedEventsEnabled, EventPayloadPolicy eventPayloadPolicy) {
            this.parameterResolvedEventsEnabled = parameterResolvedEventsEnabled;
            this.eventPayloadPolicy = eventPayloadPolicy != null ? eventPayloadPolicy : EventPayloadPolicy.passthrough();
        }

        public static EventRuntimeOptions disabled() {
            return new EventRuntimeOptions(false, EventPayloadPolicy.passthrough());
        }

        public static EventRuntimeOptions from(EventHandlingDefinition definition) {
            if (definition == null) {
                return disabled();
            }
            EventHandlingDefinition.EventConfiguration configuration = definition.getGlobalEventConfiguration();
            return new EventRuntimeOptions(
                    configuration.isEventOnParameterChanged(), configuration.getEventPayloadPolicy());
        }

        public boolean isParameterResolvedEventsEnabled() {
            return parameterResolvedEventsEnabled;
        }

        public EventPayloadPolicy getEventPayloadPolicy() {
            return eventPayloadPolicy;
        }
    }

    private final ThreadLocal<String> currentItemId = new ThreadLocal<>();
    private final ThreadLocal<Deque<UUID>> parentStack = ThreadLocal.withInitial(ArrayDeque::new);

    private final UUID executionId;
    private final String pipelineId;
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private final EventManager eventManager;
    private final ResourceFactory resourceFactory;
    private final SideComputeContext sideComputeContext = new SideComputeContext();
    private final Map<String, Object> stationScopedResources = new ConcurrentHashMap<>();
    private final AssemblyRun assemblyRun;
    private final EventRuntimeOptions eventRuntimeOptions;

    public ExecutionContext(
            UUID executionId,
            String pipelineId,
            EventManager eventManager,
            ResourceFactory resourceFactory,
            AssemblyRun assemblyRun) {
        this(executionId, pipelineId, eventManager, resourceFactory, assemblyRun, EventRuntimeOptions.disabled());
    }

    public ExecutionContext(
            UUID executionId,
            String pipelineId,
            EventManager eventManager,
            ResourceFactory resourceFactory,
            AssemblyRun assemblyRun,
            EventRuntimeOptions eventRuntimeOptions) {
        this.pipelineId = pipelineId;
        this.executionId = executionId;
        this.eventManager = eventManager;
        this.resourceFactory = resourceFactory;
        this.assemblyRun = assemblyRun;
        this.eventRuntimeOptions = eventRuntimeOptions != null ? eventRuntimeOptions : EventRuntimeOptions.disabled();
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

    public SideComputeContext getSideComputeContext() {
        return sideComputeContext;
    }

    public EventRuntimeOptions getEventRuntimeOptions() {
        return eventRuntimeOptions;
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

    public <T> T getOrCreateStationResource(String stationId, Class<T> type, Supplier<T> factory) {
        String key = stationId + ":" + type.getName();
        Object value = stationScopedResources.computeIfAbsent(key, k -> factory.get());
        return type.cast(value);
    }
}
