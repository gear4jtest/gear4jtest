package io.github.gear4jtest.core.api.context;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.api.pipeline.PipelineRuntimeContract;
import io.github.gear4jtest.core.event.EventPayloadPolicy;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.sidecompute.SideComputeContext;
import io.github.gear4jtest.core.spi.factory.IdGenerator;

public class ExecutionContext {
    private final ThreadLocal<String> currentItemId = new ThreadLocal<>();
    private final ThreadLocal<String> currentBranchId = new ThreadLocal<>();
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
    private final CancellationToken cancellationToken;

    public static Builder builder() {
        return new Builder();
    }

    private ExecutionContext(Builder builder) {
        this.pipelineId = builder.pipelineId;
        this.executionId = builder.executionId;
        this.services = Objects.requireNonNull(builder.services, "services");
        this.assemblyRun = builder.assemblyRun;
        this.eventRuntimeOptions = builder.eventRuntimeOptions != null
                ? builder.eventRuntimeOptions : EventRuntimeOptions.disabled();
        this.runtimeContract = builder.runtimeContract != null
                ? builder.runtimeContract : PipelineRuntimeContract.inlineConfigless();
        this.pipelineCallStack = builder.pipelineCallStack != null ? builder.pipelineCallStack
                : PipelineCallStack.create();
        this.idGenerator = builder.idGenerator;
        this.cancellationToken = builder.cancellationToken != null ? builder.cancellationToken
                : new CancellationToken();
    }

    public static final class Builder {
        private UUID executionId;
        private String pipelineId;
        private ExecutionServices services;
        private AssemblyRunTrace assemblyRun;
        private EventRuntimeOptions eventRuntimeOptions;
        private PipelineRuntimeContract runtimeContract;
        private PipelineCallStack pipelineCallStack;
        private IdGenerator idGenerator;
        private CancellationToken cancellationToken;

        private Builder() {
        }

        public Builder executionId(UUID executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder pipelineId(String pipelineId) {
            this.pipelineId = pipelineId;
            return this;
        }

        public Builder services(ExecutionServices services) {
            this.services = services;
            return this;
        }

        public Builder assemblyRun(AssemblyRunTrace assemblyRun) {
            this.assemblyRun = assemblyRun;
            return this;
        }

        public Builder eventRuntimeOptions(EventRuntimeOptions eventRuntimeOptions) {
            this.eventRuntimeOptions = eventRuntimeOptions;
            return this;
        }

        public Builder runtimeContract(PipelineRuntimeContract runtimeContract) {
            this.runtimeContract = runtimeContract;
            return this;
        }

        public Builder pipelineCallStack(PipelineCallStack pipelineCallStack) {
            this.pipelineCallStack = pipelineCallStack;
            return this;
        }

        public Builder idGenerator(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
            return this;
        }

        public Builder cancellationToken(CancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
            return this;
        }

        public ExecutionContext build() {
            return new ExecutionContext(this);
        }
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

    public <T> Optional<T> find(String key, Class<T> type) {
        Object value = context.get(key);
        if (value == null || !type.isInstance(value)) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    public UUID getExecutionId() {
        return executionId;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * Returns an immutable point-in-time copy of the run context.
     *
     * <p>
     * {@link #getContext()} remains mutable for existing station and side-compute
     * integrations. Runtime infrastructure should prefer this method when it only
     * needs to observe or persist the context.
     * </p>
     */
    public Map<String, Object> snapshotContext() {
        return Map.copyOf(context);
    }

    public SideComputeContext getSideComputeContext() {
        return sideComputeContext;
    }

    public AssemblyRunTrace getPipelineExecution() {
        return assemblyRun;
    }

    public ExecutionServices getServices() {
        return services;
    }

    public EventRuntimeOptions getEventRuntimeOptions() {
        return eventRuntimeOptions;
    }

    public PipelineRuntimeContract getRuntimeContract() {
        return runtimeContract;
    }

    public PipelineCallStack getPipelineCallStack() {
        return pipelineCallStack;
    }

    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    /** Returns the cooperative cancellation token shared by this run. */
    public CancellationToken getCancellationToken() {
        return cancellationToken;
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

    public Scope enterItem(String itemId) {
        String previous = currentItemId.get();
        setCurrentItemId(itemId);
        return () -> setCurrentItemId(previous);
    }

    public String getCurrentBranchId() {
        return currentBranchId.get();
    }

    public Scope enterBranch(String branchId) {
        String previous = currentBranchId.get();
        setCurrentBranchId(branchId);
        return () -> setCurrentBranchId(previous);
    }

    private void setCurrentBranchId(String branchId) {
        if (branchId == null) {
            currentBranchId.remove();
        } else {
            currentBranchId.set(branchId);
        }
    }

    public UUID getCurrentParentOperationId() {
        Deque<UUID> stack = parentStack.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public void pushParentOperationId(UUID operationId) {
        parentStack.get().push(operationId);
    }

    public Scope enterParentOperation(UUID operationId) {
        if (operationId == null) {
            return Scope.noop();
        }
        pushParentOperationId(operationId);
        return this::popParentOperationId;
    }

    public void popParentOperationId() {
        Deque<UUID> stack = parentStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        Scope NOOP = () -> {
        };

        static Scope noop() {
            return NOOP;
        }

        @Override
        void close();
    }

    public static final class EventRuntimeOptions {
        private final boolean parameterResolvedEventsEnabled;
        private final EventPayloadPolicy eventPayloadPolicy;
        private final Duration detachCleanupTimeout;

        private EventRuntimeOptions(boolean parameterResolvedEventsEnabled,
                                    EventPayloadPolicy eventPayloadPolicy,
                                    Duration detachCleanupTimeout) {
            this.parameterResolvedEventsEnabled = parameterResolvedEventsEnabled;
            this.eventPayloadPolicy = eventPayloadPolicy != null ? eventPayloadPolicy
                    : EventPayloadPolicy.passthrough();
            this.detachCleanupTimeout = detachCleanupTimeout;
        }

        public static EventRuntimeOptions disabled() {
            return new EventRuntimeOptions(false, EventPayloadPolicy.passthrough(), Duration.ofSeconds(10));
        }

        public static EventRuntimeOptions from(EventHandlingDefinition definition) {
            if (definition == null) {
                return disabled();
            }
            EventHandlingDefinition.EventConfiguration configuration = definition.getGlobalEventConfiguration();
            EventHandlingDefinition.RuntimeConfiguration runtimeConfiguration = definition.getRuntimeConfiguration();
            return new EventRuntimeOptions(configuration.isEventOnParameterChanged(),
                    configuration.getEventPayloadPolicy(), runtimeConfiguration.getDetachCleanupTimeout());
        }

        public boolean isParameterResolvedEventsEnabled() {
            return parameterResolvedEventsEnabled;
        }

        public EventPayloadPolicy getEventPayloadPolicy() {
            return eventPayloadPolicy;
        }

        public Duration detachCleanupTimeout() {
            return detachCleanupTimeout;
        }
    }
}
