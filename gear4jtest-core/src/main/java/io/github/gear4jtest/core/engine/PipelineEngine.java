package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.PipelineExecutor;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.api.pipeline.PipelineReference;
import io.github.gear4jtest.core.api.station.PipelineCallStation;
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

public class PipelineEngine implements PipelineExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineEngine.class);
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
    private final PipelineExecutionContextFactory executionContextFactory;
    private final PipelineRootExecutionChain rootExecutionChain;
    private final PipelineRunLifecycleInvoker lifecycleInvoker;
    private final DetachedEventRuntimeCleanupScheduler detachedCleanupScheduler;

    private PipelineEngine(Builder builder) {
        this.resourceFactory = Objects.requireNonNull(builder.resourceFactory, "ResourceFactory must not be null");
        this.extensionResolver = Objects.requireNonNull(builder.extensionResolver,
                                                        "Extension resolver must not be null");
        this.executionContextRegistry = Objects.requireNonNull(builder.executionContextRegistry,
                                                               "ExecutionContextRegistry must not be null");
        this.defaultIdGenerator = builder.idGenerator != null ? builder.idGenerator : IdGenerator.defaultGenerator();
        this.taskFactory = builder.taskFactory != null ? builder.taskFactory : new TaskFactory();
        this.payloadCloner = builder.payloadCloner != null ? builder.payloadCloner : PayloadCloners.immutableAware();
        this.workerConcurrencyConfiguration = PipelineEngineConfiguration.effectiveWorkerConcurrencyConfiguration(
                                                                                                                  builder.workerConcurrencyConfiguration,
                                                                                                                  builder.workerConcurrencyPolicy,
                                                                                                                  builder.workerLockAcquisitionPolicy,
                                                                                                                  builder.workerLockWaitTimeout,
                                                                                                                  builder.workerConcurrencyRegistryConfiguration,
                                                                                                                  builder.workerConcurrencyManager);
        this.workerConcurrencyManager = builder.workerConcurrencyManager != null ? builder.workerConcurrencyManager
                : PipelineEngineConfiguration.defaultWorkerConcurrencyManager(this.workerConcurrencyConfiguration);
        this.parallelExecutionConfiguration = builder.parallelExecutionConfiguration != null
                ? builder.parallelExecutionConfiguration : ParallelExecutionConfiguration.defaults();
        this.runnerChainFactory = builder.runnerChainFactory != null ? builder.runnerChainFactory
                : new RunnerChainFactory(
                        StrategyRegistry.defaultRegistry(this::executeNestedPipeline, this.workerConcurrencyManager,
                                                         this.workerConcurrencyConfiguration,
                                                         this.parallelExecutionConfiguration));
        this.executionContextFactory = new PipelineExecutionContextFactory(this.resourceFactory,
                this.executionContextRegistry,
                this.defaultIdGenerator);
        this.rootExecutionChain = new PipelineRootExecutionChain(this.runnerChainFactory);
        this.lifecycleInvoker = new PipelineRunLifecycleInvoker();
        this.detachedCleanupScheduler = new DetachedEventRuntimeCleanupScheduler();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest request) {
        Objects.requireNonNull(pipeline, "pipeline must not be null");
        RunRequest effectiveRequest = request != null ? request : RunRequest.builder().build();
        PipelineCallStack callStack = effectiveRequest.getPipelineCallStack() != null
                ? effectiveRequest.getPipelineCallStack().copy() : new PipelineCallStack();

        try (PipelineCallStack.Scope ignored = callStack.enter(PipelineReference.from(pipeline))) {
            return executeWithCallStack(pipeline, effectiveRequest, callStack);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private ExecutionResult<?> executeNestedPipeline(PipelineCallStation<?, ?> station,
                                                     AssemblyLine<?, ?> childPipeline,
                                                     Object input,
                                                     StationExecutionContext parentContext) {
        RunRequest childRequest = NestedRunRequestFactory.create(input, parentContext, defaultIdGenerator);
        return execute((AssemblyLine) childPipeline, childRequest);
    }

    private <IN, OUT> ExecutionResult<OUT> executeWithCallStack(AssemblyLine<IN, OUT> pipeline,
                                                                RunRequest request,
                                                                PipelineCallStack callStack) {
        logStart(pipeline, request);

        ResolvedExtensions resolvedExtensions = extensionResolver.resolve(pipeline, request);
        EventHandlingDefinition eventHandlingDefinition = OptionalEventHandlingDefinition.from(pipeline);
        EventManager eventManager = new EventManager(eventHandlingDefinition, executionContextRegistry);
        ExecutionSupport support = PipelineRunSupportFactory.create(resolvedExtensions, taskFactory, payloadCloner);
        PipelineRunContext runContext = executionContextFactory.create(pipeline, request, callStack,
                                                                       eventHandlingDefinition, eventManager);
        ExecutionContext context = runContext.context();

        try {
            runContext.execution().setStartTime(Instant.now());
            runContext.execution().setStatus(ExecutionStatus.RUNNING);
            return executeRegisteredContext(pipeline, request, resolvedExtensions, support, runContext);
        } finally {
            shutdownEventRuntimeAndCleanup(eventManager, context);
        }
    }

    private <IN, OUT> ExecutionResult<OUT> executeRegisteredContext(AssemblyLine<IN, OUT> pipeline,
                                                                    RunRequest request,
                                                                    ResolvedExtensions resolvedExtensions,
                                                                    ExecutionSupport support,
                                                                    PipelineRunContext runContext) {
        ExecutionResult<OUT> result = null;
        Throwable fatalError = null;
        try {
            lifecycleInvoker.invokeRunStarted(resolvedExtensions.runLifecycleExtensions(), runContext.context(),
                                              runContext.execution());
            result = rootExecutionChain.execute(pipeline, request, runContext.context(), support, resolvedExtensions,
                                                runContext.execution());
        } catch (Exception e) {
            LOGGER.error("Error while executing pipeline", e);
            runContext.execution().setStatus(ExecutionStatus.FAILED);
            runContext.execution().setError(PipelineExecutionResultMapper.asException(e));
            result = ExecutionResult.failure(PipelineExecutionResultMapper.asException(e), runContext.execution());
        } catch (Throwable t) {
            fatalError = t;
            throw t;
        } finally {
            PipelineExecutionResultMapper.finalizeRunFromResult(runContext.context(), runContext.execution(), result,
                                                                fatalError);
            if (result == null && fatalError == null) {
                Exception failure = runContext.execution().getError() != null ? runContext.execution().getError()
                        : new IllegalStateException("Pipeline execution returned no result");
                result = ExecutionResult.failure(failure, runContext.execution());
            }
            Exception completionFailure = lifecycleInvoker
                    .invokeRunCompleted(resolvedExtensions.runLifecycleExtensions(),
                                        runContext.context(),
                                        runContext.execution());
            if (completionFailure != null && fatalError == null) {
                runContext.execution().setEndTime(Instant.now());
                runContext.execution().setStatus(ExecutionStatus.FAILED);
                runContext.execution().setError(completionFailure);
                result = ExecutionResult.failure(completionFailure, runContext.execution());
            }
        }
        return result;
    }

    private void shutdownEventRuntimeAndCleanup(EventManager eventManager, ExecutionContext context) {
        EventManager.ShutdownHandle shutdownHandle = eventManager.shutdown();
        Runnable cleanup = PipelineRunCleanup.cleanup(context, executionContextRegistry);
        if (shutdownHandle.detached()) {
            detachedCleanupScheduler.schedule(cleanup, shutdownHandle.completion(),
                                              context.getEventRuntimeOptions().detachCleanupTimeout());
        } else {
            cleanup.run();
        }
    }

    private static <IN, OUT> void logStart(AssemblyLine<IN, OUT> pipeline, RunRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting pipeline execution. pipelineId={}, rootStation={}, requestExtensions={}",
                         pipeline.getId(), pipeline.getRootStation() != null ? pipeline.getRootStation().getId() : null,
                         request.getExtensions().stream().map(e -> e.getClass().getSimpleName()).toList());
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

        public PipelineEngine build() {
            return new PipelineEngine(this);
        }
    }
}
