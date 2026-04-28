package io.github.gear4jtest.core.api.context;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.api.pipeline.PipelineRuntimeContract;
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.sidecompute.SideComputeContext;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final SideComputeContext sideComputeContext = new SideComputeContext();
    private final AssemblyRunTrace assemblyRun;
    private final ExecutionServices services;
    private final EventRuntimeOptions eventRuntimeOptions;
    private final PipelineRuntimeContract runtimeContract;
    private final PipelineCallStack pipelineCallStack;
    private final IdGenerator idGenerator;

    public ExecutionContext(UUID executionId, String pipelineId, ExecutionServices services, AssemblyRunTrace assemblyRun) {
        this(executionId, pipelineId, services, assemblyRun, EventRuntimeOptions.disabled(), null, new PipelineCallStack(), null);
    }

    public ExecutionContext(
            UUID executionId,
            String pipelineId,
            ExecutionServices services,
            AssemblyRunTrace assemblyRun,
            EventRuntimeOptions eventRuntimeOptions) {
        this(executionId, pipelineId, services, assemblyRun, eventRuntimeOptions, null, new PipelineCallStack(), null);
    }

    public ExecutionContext(
            UUID executionId,
            String pipelineId,
            ExecutionServices services,
            AssemblyRunTrace assemblyRun,
            EventRuntimeOptions eventRuntimeOptions,
            PipelineRuntimeContract runtimeContract,
            PipelineCallStack pipelineCallStack) {
        this(executionId, pipelineId, services, assemblyRun, eventRuntimeOptions, runtimeContract, pipelineCallStack, null);
    }

    public ExecutionContext(
            UUID executionId,
            String pipelineId,
            ExecutionServices services,
            AssemblyRunTrace assemblyRun,
            EventRuntimeOptions eventRuntimeOptions,
            PipelineRuntimeContract runtimeContract,
            PipelineCallStack pipelineCallStack,
            IdGenerator idGenerator) {
        this.pipelineId = pipelineId;
        this.executionId = executionId;
        this.services = Objects.requireNonNull(services, "services");
        this.assemblyRun = assemblyRun;
        this.eventRuntimeOptions = eventRuntimeOptions != null ? eventRuntimeOptions : EventRuntimeOptions.disabled();
        this.runtimeContract = runtimeContract != null ? runtimeContract : PipelineRuntimeContract.inlineConfigless();
        this.pipelineCallStack = pipelineCallStack != null ? pipelineCallStack : new PipelineCallStack();
        this.idGenerator = idGenerator;
    }

    public String getPipelineId() { return pipelineId; }
    public <T> void put(String key, T value) { context.put(key, value); }
    public <T> T get(String key, Class<T> type) { return type.cast(context.get(key)); }
    public UUID getExecutionId() { return executionId; }
    public Map<String, Object> getContext() { return context; }
    public SideComputeContext getSideComputeContext() { return sideComputeContext; }
    public AssemblyRunTrace getPipelineExecution() { return assemblyRun; }
    public ExecutionServices getServices() { return services; }
    public EventRuntimeOptions getEventRuntimeOptions() { return eventRuntimeOptions; }
    public PipelineRuntimeContract getRuntimeContract() { return runtimeContract; }
    public PipelineCallStack getPipelineCallStack() { return pipelineCallStack; }
    public IdGenerator getIdGenerator() { return idGenerator; }

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
}
