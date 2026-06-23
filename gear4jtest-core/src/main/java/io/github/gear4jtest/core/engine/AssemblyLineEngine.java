package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineCallStack;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineReference;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyRegistryConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class AssemblyLineEngine implements AssemblyLineExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AssemblyLineEngine.class);
    private final ResourceFactory resourceFactory;
    private final RunnerChainFactory runnerChainFactory;
    private final RuntimeExtensionResolver extensionResolver;
    private final ExecutionContextRegistry executionContextRegistry;
    private final IdGenerator defaultIdGenerator;
    private final TaskFactory taskFactory;
    private final PayloadCloner payloadCloner;
    private final WorkerConcurrencyManager workerConcurrencyManager;
    private final WorkerConcurrencyConfiguration workerConcurrencyConfiguration;
    private final ParallelExecutionConfiguration parallelExecutionConfiguration;
    private final ContextPropagationPolicy nestedRunContextPropagationPolicy;
    private final AssemblyLineExecutionContextFactory executionContextFactory;
    private final AssemblyLineRootExecutionChain rootExecutionChain;
    private final AssemblyLineRunLifecycleInvoker lifecycleInvoker;
    private final DetachedEventRuntimeCleanupScheduler detachedCleanupScheduler;

    private AssemblyLineEngine(Builder builder) {
        this.resourceFactory = Objects.requireNonNull(builder.resourceFactory, "ResourceFactory must not be null");
        this.extensionResolver = Objects.requireNonNull(builder.extensionResolver,
                                                        "Extension resolver must not be null");
        this.executionContextRegistry = Objects.requireNonNull(builder.executionContextRegistry,
                                                               "ExecutionContextRegistry must not be null");
        this.defaultIdGenerator = builder.idGenerator != null ? builder.idGenerator : IdGenerator.defaultGenerator();
        this.taskFactory = builder.taskFactory != null ? builder.taskFactory : new TaskFactory();
        this.payloadCloner = builder.payloadCloner != null ? builder.payloadCloner : PayloadCloners.immutableAware();
        this.workerConcurrencyConfiguration = AssemblyLineEngineConfiguration.effectiveWorkerConcurrencyConfiguration(
                                                                                                                      builder.workerConcurrencyConfiguration,
                                                                                                                      builder.workerConcurrencyPolicy,
                                                                                                                      builder.workerLockAcquisitionPolicy,
                                                                                                                      builder.workerLockWaitTimeout,
                                                                                                                      builder.workerConcurrencyRegistryConfiguration,
                                                                                                                      builder.workerConcurrencyManager);
        this.workerConcurrencyManager = builder.workerConcurrencyManager != null ? builder.workerConcurrencyManager
                : AssemblyLineEngineConfiguration.defaultWorkerConcurrencyManager(this.workerConcurrencyConfiguration);
        this.parallelExecutionConfiguration = builder.parallelExecutionConfiguration != null
                ? builder.parallelExecutionConfiguration : ParallelExecutionConfiguration.defaults();
        this.nestedRunContextPropagationPolicy = builder.nestedRunContextPropagationPolicy != null
                ? builder.nestedRunContextPropagationPolicy : ContextPropagationPolicy.inheritAllShallow();
        this.runnerChainFactory = builder.runnerChainFactory != null ? builder.runnerChainFactory
                : new RunnerChainFactory(
                        StrategyRegistry.defaultRegistry(this::executeNestedAssemblyLine, this.workerConcurrencyManager,
                                                         this.workerConcurrencyConfiguration,
                                                         this.parallelExecutionConfiguration));
        this.executionContextFactory = new AssemblyLineExecutionContextFactory(this.resourceFactory,
                this.executionContextRegistry,
                this.defaultIdGenerator);
        this.rootExecutionChain = new AssemblyLineRootExecutionChain(this.runnerChainFactory);
        this.lifecycleInvoker = new AssemblyLineRunLifecycleInvoker();
        this.detachedCleanupScheduler = new DetachedEventRuntimeCleanupScheduler();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest request) {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
        RunRequest effectiveRequest = request != null ? request : RunRequest.builder().build();
        AssemblyLineCallStack callStack = effectiveRequest.getAssemblyLineCallStack() != null
                ? effectiveRequest.getAssemblyLineCallStack().copy() : AssemblyLineCallStack.create();

        try (AssemblyLineCallStack.Scope ignored = callStack.enter(AssemblyLineReference.from(pipeline))) {
            return executeWithCallStack(pipeline, effectiveRequest, callStack);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ExecutionResult<?> executeNestedAssemblyLine(AssemblyLineCallStation<?, ?> station,
                                                         AssemblyLine<?, ?> childAssemblyLine,
                                                         Object input,
                                                         StationExecutionContext parentContext) {
        RunRequest childRequest = NestedRunRequestFactory.create(input, parentContext, defaultIdGenerator,
                                                                 nestedRunContextPropagationPolicy);
        return execute((AssemblyLine) childAssemblyLine, childRequest);
    }

    private <IN, OUT> ExecutionResult<OUT> executeWithCallStack(AssemblyLine<IN, OUT> pipeline,
                                                                RunRequest request,
                                                                AssemblyLineCallStack callStack) {
        ResolvedExtensions resolvedExtensions = extensionResolver.resolve(pipeline, request);
        EventHandlingDefinition eventHandlingDefinition = OptionalEventHandlingDefinition.from(pipeline);
        EventManager eventManager = new EventManager(eventHandlingDefinition, executionContextRegistry);
        ExecutionSupport support = AssemblyLineRunSupportFactory.create(resolvedExtensions, taskFactory, payloadCloner);
        AssemblyLineRunContext runContext = executionContextFactory.create(pipeline, request, callStack,
                                                                           eventHandlingDefinition, eventManager);
        ExecutionContext context = runContext.context();

        try (MdcScope ignored = MdcScope.open(context)) {
            logStart(pipeline, request);
            try {
                runContext.execution().setStartTime(Instant.now());
                runContext.execution().setStatus(ExecutionStatus.RUNNING);
                return executeRegisteredContext(pipeline, request, resolvedExtensions, support, runContext);
            } finally {
                shutdownEventRuntimeAndCleanup(eventManager, context);
            }
        }
    }

    private <IN, OUT> ExecutionResult<OUT> executeRegisteredContext(AssemblyLine<IN, OUT> pipeline,
                                                                    RunRequest request,
                                                                    ResolvedExtensions resolvedExtensions,
                                                                    ExecutionSupport support,
                                                                    AssemblyLineRunContext runContext) {
        ExecutionResult<OUT> result = null;
        boolean recoverablePath = false;
        try {
            lifecycleInvoker.invokeRunStarted(resolvedExtensions.runLifecycleExtensions(), runContext.context(),
                                              runContext.execution());
            result = rootExecutionChain.execute(pipeline, request, runContext.context(), support, resolvedExtensions,
                                                runContext.execution());
            recoverablePath = true;
        } catch (Exception e) {
            recoverablePath = true;
            LOGGER.error("Error while executing pipeline", e);
            runContext.execution().setStatus(ExecutionStatus.FAILED);
            runContext.execution().setError(AssemblyLineExecutionResultMapper.asException(e));
            result = ExecutionResult.failure(AssemblyLineExecutionResultMapper.asException(e), runContext.execution());
        } finally {
            if (recoverablePath) {
                result = finalizeRecoverableExecution(resolvedExtensions, runContext, result);
            }
        }
        return result;
    }

    private <OUT> ExecutionResult<OUT> finalizeRecoverableExecution(ResolvedExtensions resolvedExtensions,
                                                                    AssemblyLineRunContext runContext,
                                                                    ExecutionResult<OUT> result) {
        ExecutionResult<OUT> finalizedResult = result;
        AssemblyLineExecutionResultMapper.finalizeRunFromResult(runContext.context(), runContext.execution(),
                                                                finalizedResult, null);
        if (finalizedResult == null) {
            Exception failure = runContext.execution().getError() != null ? runContext.execution().getError()
                    : new IllegalStateException("AssemblyLine execution returned no result");
            finalizedResult = ExecutionResult.failure(failure, runContext.execution());
        }
        Exception completionFailure = lifecycleInvoker
                .invokeRunCompleted(resolvedExtensions.runLifecycleExtensions(),
                                    runContext.context(),
                                    runContext.execution());
        if (completionFailure != null) {
            runContext.execution().setEndTime(Instant.now());
            runContext.execution().setStatus(ExecutionStatus.FAILED);
            runContext.execution().setError(completionFailure);
            finalizedResult = ExecutionResult.failure(completionFailure, runContext.execution());
        }
        return finalizedResult;
    }

    private void shutdownEventRuntimeAndCleanup(EventManager eventManager, ExecutionContext context) {
        EventManager.ShutdownHandle shutdownHandle = eventManager.shutdown();
        Runnable cleanup = AssemblyLineRunCleanup.cleanup(context, executionContextRegistry);
        if (shutdownHandle.detached()) {
            detachedCleanupScheduler.schedule(cleanup, shutdownHandle.completion(),
                                              context.getEventRuntimeOptions().detachCleanupTimeout());
        } else {
            cleanup.run();
        }
    }

    private static <IN, OUT> void logStart(AssemblyLine<IN, OUT> pipeline, RunRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting pipeline execution. assemblyLineId={}, rootStation={}, requestExtensions={}",
                         pipeline.getId(), pipeline.getRootStation() != null ? pipeline.getRootStation().getId() : null,
                         request.getExtensions().stream().map(e -> e.getClass().getSimpleName()).toList());
        }
    }

    private static final class MdcScope implements AutoCloseable {
        private static final String EXECUTION_ID = "gear4j.executionId";
        private static final String ASSEMBLY_LINE_ID = "gear4j.assemblyLineId";

        private final String previousExecutionId;
        private final String previousAssemblyLineId;

        private MdcScope(String previousExecutionId, String previousAssemblyLineId) {
            this.previousExecutionId = previousExecutionId;
            this.previousAssemblyLineId = previousAssemblyLineId;
        }

        static MdcScope open(ExecutionContext context) {
            String previousExecutionId = MDC.get(EXECUTION_ID);
            String previousAssemblyLineId = MDC.get(ASSEMBLY_LINE_ID);
            putOrRemove(EXECUTION_ID, context.getExecutionId() != null ? context.getExecutionId().toString() : null);
            putOrRemove(ASSEMBLY_LINE_ID, context.getAssemblyLineId());
            return new MdcScope(previousExecutionId, previousAssemblyLineId);
        }

        @Override
        public void close() {
            restore(EXECUTION_ID, previousExecutionId);
            restore(ASSEMBLY_LINE_ID, previousAssemblyLineId);
        }

        private static void restore(String key, String previousValue) {
            putOrRemove(key, previousValue);
        }

        private static void putOrRemove(String key, String value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
    }

    public static final class Builder {
        private ResourceFactory resourceFactory;
        private RunnerChainFactory runnerChainFactory;
        private RuntimeExtensionResolver extensionResolver;
        private ExecutionContextRegistry executionContextRegistry;
        private IdGenerator idGenerator;
        private TaskFactory taskFactory;
        private PayloadCloner payloadCloner;
        private WorkerConcurrencyManager workerConcurrencyManager;
        private WorkerConcurrencyConfiguration workerConcurrencyConfiguration;
        private WorkerConcurrencyPolicy workerConcurrencyPolicy;
        private WorkerLockAcquisitionPolicy workerLockAcquisitionPolicy;
        private Duration workerLockWaitTimeout;
        private WorkerConcurrencyRegistryConfiguration workerConcurrencyRegistryConfiguration;
        private ParallelExecutionConfiguration parallelExecutionConfiguration;
        private ContextPropagationPolicy nestedRunContextPropagationPolicy;

        public Builder resourceFactory(ResourceFactory resourceFactory) {
            this.resourceFactory = resourceFactory;
            return this;
        }

        public Builder runnerChainFactory(RunnerChainFactory runnerChainFactory) {
            this.runnerChainFactory = runnerChainFactory;
            return this;
        }

        public Builder extensionResolver(RuntimeExtensionResolver extensionResolver) {
            this.extensionResolver = extensionResolver;
            return this;
        }

        public Builder executionContextRegistry(ExecutionContextRegistry executionContextRegistry) {
            this.executionContextRegistry = executionContextRegistry;
            return this;
        }

        public Builder idGenerator(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
            return this;
        }

        public Builder taskFactory(TaskFactory taskFactory) {
            this.taskFactory = taskFactory;
            return this;
        }

        public Builder payloadCloner(PayloadCloner payloadCloner) {
            this.payloadCloner = payloadCloner;
            return this;
        }

        public Builder workerConcurrencyManager(WorkerConcurrencyManager workerConcurrencyManager) {
            this.workerConcurrencyManager = workerConcurrencyManager;
            return this;
        }

        public Builder workerConcurrencyConfiguration(WorkerConcurrencyConfiguration workerConcurrencyConfiguration) {
            this.workerConcurrencyConfiguration = workerConcurrencyConfiguration;
            return this;
        }

        public Builder workerConcurrencyPolicy(WorkerConcurrencyPolicy workerConcurrencyPolicy) {
            this.workerConcurrencyPolicy = workerConcurrencyPolicy;
            return this;
        }

        public Builder workerLockAcquisitionPolicy(WorkerLockAcquisitionPolicy workerLockAcquisitionPolicy) {
            this.workerLockAcquisitionPolicy = workerLockAcquisitionPolicy;
            return this;
        }

        public Builder workerLockWaitTimeout(Duration workerLockWaitTimeout) {
            this.workerLockWaitTimeout = workerLockWaitTimeout;
            return this;
        }

        public Builder workerConcurrencyRegistryConfiguration(
                                                              WorkerConcurrencyRegistryConfiguration workerConcurrencyRegistryConfiguration) {
            this.workerConcurrencyRegistryConfiguration = workerConcurrencyRegistryConfiguration;
            return this;
        }

        public Builder parallelExecutionConfiguration(ParallelExecutionConfiguration parallelExecutionConfiguration) {
            this.parallelExecutionConfiguration = parallelExecutionConfiguration;
            return this;
        }

        /**
         * Configures how user context values are propagated from a parent run to a
         * {@code NESTED_RUN} child. The default is
         * {@link ContextPropagationPolicy#inheritAllShallow()}.
         */
        public Builder nestedRunContextPropagationPolicy(ContextPropagationPolicy nestedRunContextPropagationPolicy) {
            this.nestedRunContextPropagationPolicy = nestedRunContextPropagationPolicy;
            return this;
        }

        public AssemblyLineEngine build() {
            return new AssemblyLineEngine(this);
        }
    }
}
