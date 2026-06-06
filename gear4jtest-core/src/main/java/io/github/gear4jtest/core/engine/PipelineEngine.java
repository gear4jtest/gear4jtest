package io.github.gear4jtest.core.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionOutcome;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.PipelineExecutor;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.context.StationScopedResourceRegistry;
import io.github.gear4jtest.core.api.pipeline.NestedRunContext;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.api.pipeline.PipelineReference;
import io.github.gear4jtest.core.api.station.PipelineCallStation;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.engine.support.ExecutorDecorator;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyManager;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyPolicy;
import io.github.gear4jtest.core.engine.support.WorkerConcurrencyRegistryConfiguration;
import io.github.gear4jtest.core.engine.support.WorkerLockAcquisitionPolicy;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension.RunChain;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
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

    private PipelineEngine(Builder builder) {
        this.resourceFactory = Objects.requireNonNull(builder.resourceFactory, "ResourceFactory must not be null");
        this.extensionResolver = Objects.requireNonNull(builder.extensionResolver,
                                                        "Extension resolver must not be null");
        this.executionContextRegistry = Objects.requireNonNull(builder.executionContextRegistry,
                                                               "ExecutionContextRegistry must not be null");
        this.defaultIdGenerator = builder.idGenerator != null ? builder.idGenerator : IdGenerator.defaultGenerator();
        this.taskFactory = builder.taskFactory != null ? builder.taskFactory : new TaskFactory();
        this.payloadCloner = builder.payloadCloner != null ? builder.payloadCloner : PayloadCloners.immutableAware();
        this.workerConcurrencyConfiguration = effectiveWorkerConcurrencyConfiguration(builder);
        this.workerConcurrencyManager = builder.workerConcurrencyManager != null ? builder.workerConcurrencyManager
                : defaultWorkerConcurrencyManager(this.workerConcurrencyConfiguration);
        this.parallelExecutionConfiguration = builder.parallelExecutionConfiguration != null
                ? builder.parallelExecutionConfiguration : ParallelExecutionConfiguration.defaults();
        this.runnerChainFactory = builder.runnerChainFactory != null ? builder.runnerChainFactory
                : new RunnerChainFactory(
                        StrategyRegistry.defaultRegistry(this::executeNestedPipeline, this.workerConcurrencyManager,
                                                         this.workerConcurrencyConfiguration,
                                                         this.parallelExecutionConfiguration));
    }

    private static WorkerConcurrencyConfiguration effectiveWorkerConcurrencyConfiguration(Builder builder) {
        WorkerConcurrencyConfiguration configuration = builder.workerConcurrencyConfiguration != null
                ? builder.workerConcurrencyConfiguration
                : WorkerConcurrencyConfiguration.defaults();

        if (builder.workerConcurrencyPolicy != null) {
            configuration = configuration.withConcurrencyPolicy(builder.workerConcurrencyPolicy);
        } else if (builder.workerConcurrencyConfiguration == null && builder.workerConcurrencyManager != null) {
            configuration = configuration
                    .withConcurrencyPolicy(WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE);
        }

        if (builder.workerLockAcquisitionPolicy != null) {
            configuration = configuration.withLockAcquisitionPolicy(builder.workerLockAcquisitionPolicy);
        }
        if (builder.workerLockWaitTimeout != null) {
            configuration = configuration.withLockWaitTimeout(builder.workerLockWaitTimeout);
        }
        if (builder.workerConcurrencyRegistryConfiguration != null) {
            configuration = configuration.withRegistryConfiguration(builder.workerConcurrencyRegistryConfiguration);
        }
        return configuration;
    }

    private static WorkerConcurrencyManager defaultWorkerConcurrencyManager(WorkerConcurrencyConfiguration configuration) {
        return switch (configuration.concurrencyPolicy()) {
            case LOCK_PER_WORKER_INSTANCE -> WorkerConcurrencyManager.global();
            case ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE, ALLOW_PARALLEL_INVOCATIONS -> new WorkerConcurrencyManager(
                    configuration.registryConfiguration());
        };
    }

    private static void applyNestedRunContext(AssemblyRunTrace execution, NestedRunContext nestedRunContext) {
        if (nestedRunContext == null) {
            return;
        }
        execution.setParentExecutionId(nestedRunContext.parentExecutionId());
        execution.setRootExecutionId(nestedRunContext.rootExecutionId());
        execution.setParentStationLogId(nestedRunContext.parentStationLogId());
    }

    @SuppressWarnings("unchecked")
    private static <IN, OUT> ExecutionResult<OUT> doExecuteInternal(AssemblyLine<IN, OUT> pipeline,
                                                                    RunRequest request,
                                                                    StationRunner rootRunner,
                                                                    StationExecutionContext rootContext,
                                                                    ExecutionContext ctx,
                                                                    AssemblyRunTrace execution) {

        StationLogTrace rootLog = rootRunner.run(request.getInput(), pipeline.getRootStation(), rootContext);
        Object result = rootLog.getOutput();

        return switch (rootLog.getStatus()) {
            case SUCCEEDED, SKIPPED -> {
                execution.setStatus(ExecutionStatus.SUCCEEDED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.success(result, execution);
            }
            case STOPPED -> {
                execution.setStatus(ExecutionStatus.STOPPED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.stopped(result, execution);
            }
            case CANCELLED -> {
                Exception cancellation = rootLog.getErrorMessage() != null
                        ? new RuntimeException(rootLog.getErrorMessage()) : null;
                execution.setStatus(ExecutionStatus.CANCELLED);
                execution.setResult(result);
                if (cancellation != null) {
                    execution.setError(cancellation);
                }
                yield (ExecutionResult<OUT>) ExecutionResult.cancelled(result, execution, cancellation);
            }
            case FAILED, RUNNING -> {
                Exception failure = new RuntimeException(
                        rootLog.getErrorMessage() != null ? rootLog.getErrorMessage() : "Pipeline failed");
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setError(failure);
                yield ExecutionResult.failure(failure, execution);
            }
        };
    }

    private static void finalizeRunFromResult(ExecutionContext ctx,
                                              AssemblyRunTrace execution,
                                              ExecutionResult<?> result,
                                              Throwable fatalError) {
        if (execution.getEndTime() == null) {
            execution.setEndTime(Instant.now());
        }

        try {
            execution.setContext(new HashMap<>(ctx.getContext()));
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to capture execution context for run {}. The run trace will keep its previous context.",
                        execution.getId(), throwable);
        }

        if (fatalError != null) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage("CRITICAL JVM ERROR: " + fatalError);
        } else if (result != null) {
            execution.setResult(result.getResult());
            switch (result.getOutcome()) {
                case SUCCEEDED -> execution.setStatus(ExecutionStatus.SUCCEEDED);
                case STOPPED -> execution.setStatus(ExecutionStatus.STOPPED);
                case CANCELLED -> execution.setStatus(ExecutionStatus.CANCELLED);
                case FAILED -> execution.setStatus(ExecutionStatus.FAILED);
            }
            if (result.getOutcome() == ExecutionOutcome.FAILED || result.getOutcome() == ExecutionOutcome.CANCELLED) {
                if (result.getError() != null) {
                    execution.setError(asException(result.getError()));
                }
            }
        } else {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setError(new IllegalStateException("Pipeline execution returned no result"));
        }
    }

    private static Exception asException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
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
        NestedRunContext nestedRunContext = NestedRunContext.from(parentContext);

        /*
         * NESTED_RUN currently inherits the full key/value context from the parent run.
         * This is an explicit MVP choice. A future ContextPropagationPolicy can narrow
         * this to NONE, ALL or an explicit projection without changing the
         * PipelineCallStation contract.
         */
        RunRequest childRequest = RunRequest.builder().input(input)
                .context(new HashMap<>(parentContext.getGlobalContext().getContext()))
                .resourceFactory(parentContext.getServices().getResourceFactory())
                .withIdGenerator(Optional.ofNullable(parentContext.getGlobalContext().getIdGenerator())
                        .orElse(defaultIdGenerator))
                .nestedRunContext(nestedRunContext)
                .pipelineCallStack(parentContext.getGlobalContext().getPipelineCallStack())
                .cancellationToken(parentContext.getGlobalContext().getCancellationToken()).build();

        return execute((AssemblyLine) childPipeline, childRequest);
    }

    private <IN, OUT> ExecutionResult<OUT> executeWithCallStack(AssemblyLine<IN, OUT> pipeline,
                                                                RunRequest request,
                                                                PipelineCallStack callStack) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Starting pipeline execution. pipelineId={}, rootStation={}, requestExtensions={}",
                         pipeline.getId(), pipeline.getRootStation() != null ? pipeline.getRootStation().getId() : null,
                         request.getExtensions().stream().map(e -> e.getClass().getSimpleName()).toList());
        }

        ResolvedExtensions resolvedExtensions = extensionResolver.resolve(pipeline, request);

        EventHandlingDefinition eventHandlingDefinition = Optional.ofNullable(pipeline.getConfiguration())
                .map(AssemblyLine.Configuration::getEventHandlingDefinition).orElse(null);
        EventManager eventManager = new EventManager(eventHandlingDefinition, executionContextRegistry);

        ExecutorDecorator decorator = (rawExec, context) -> {
            ExecutorService wrapped = rawExec;
            for (var wrapperExt : resolvedExtensions.executorWrappers()) {
                wrapped = wrapperExt.wrapExecutor(wrapped, context);
            }
            return wrapped;
        };

        ExecutionSupport support = new ExecutionSupport(decorator, taskFactory, payloadCloner);

        Map<String, Object> effectiveContext = new HashMap<>(pipeline.getDefaultContext());
        if (request.getContext() != null) {
            effectiveContext.putAll(request.getContext());
        }

        ExecutionContext.EventRuntimeOptions eventRuntimeOptions = ExecutionContext.EventRuntimeOptions
                .from(eventHandlingDefinition);
        IdGenerator effectiveGenerator = Optional.ofNullable(request.getIdGenerator()).orElse(this.defaultIdGenerator);

        var executionId = effectiveGenerator.generate();
        var execution = new AssemblyRunTrace(executionId, pipeline.getId(), new HashMap<>(effectiveContext));
        applyNestedRunContext(execution, request.getNestedRunContext());

        var effectiveResourceFactory = Optional.ofNullable(request.getResourceFactory())
                .or(() -> Optional.ofNullable(this.resourceFactory)).orElseThrow();

        ExecutionServices services = new ExecutionServices(eventManager, effectiveResourceFactory,
                new StationScopedResourceRegistry());

        var ctx = new ExecutionContext(executionId, pipeline.getId(), services, execution, eventRuntimeOptions,
                pipeline.getConfiguration().getRuntimeContract(), callStack, effectiveGenerator,
                request.getCancellationToken());
        ctx.getContext().putAll(effectiveContext);

        executionContextRegistry.register(ctx);

        try {
            for (RunLifecycleExtension lifecycleExtension : resolvedExtensions.runLifecycleExtensions()) {
                invokeRunStartedSafely(lifecycleExtension, ctx, execution);
            }

            execution.setStartTime(Instant.now());
            execution.setStatus(ExecutionStatus.RUNNING);

            StationRunner rootRunner = runnerChainFactory.createRootRunner(pipeline, request, ctx, resolvedExtensions);
            StationExecutionContext rootContext = new DefaultStationExecutionContext("root-invoker", ctx, support);

            List<RunInterceptorExtension> interceptors = resolvedExtensions.runInterceptors();
            RunChain<IN, OUT> chain = () -> doExecuteInternal(pipeline, request, rootRunner, rootContext, ctx,
                                                              execution);

            for (int i = interceptors.size() - 1; i >= 0; i--) {
                RunInterceptorExtension interceptor = interceptors.get(i);
                RunChain<IN, OUT> next = chain;
                chain = () -> interceptor.aroundRun(pipeline, request, ctx, next);
            }

            ExecutionResult<OUT> result = null;
            Throwable fatalError = null;
            try {
                result = chain.proceed();
                return result;
            } catch (Exception e) {
                LOGGER.error("Error while executing pipeline", e);
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setError(asException(e));
                result = ExecutionResult.failure(e, execution);
                return result;
            } catch (Throwable t) {
                fatalError = t;
                throw t;
            } finally {
                finalizeRunFromResult(ctx, execution, result, fatalError);

                for (RunLifecycleExtension lifecycleExtension : resolvedExtensions.runLifecycleExtensions()) {
                    invokeRunCompletedSafely(lifecycleExtension, ctx, execution);
                }
            }
        } finally {
            EventManager.ShutdownHandle shutdownHandle = eventManager.shutdown();
            Runnable cleanup = () -> {
                try {
                    ctx.getSideComputeContext().cancelUnresolvedFutures();
                    ctx.getServices().getStationScopedResources().clearAll();
                } finally {
                    executionContextRegistry.remove(ctx.getExecutionId());
                }
            };

            if (shutdownHandle.detached()) {
                scheduleDetachedCleanup(cleanup, shutdownHandle.completion(),
                                        eventRuntimeOptions.detachCleanupTimeout());
            } else {
                cleanup.run();
            }
        }
    }

    private void scheduleDetachedCleanup(Runnable cleanup,
                                         CompletableFuture<Void> completion,
                                         Duration detachCleanupTimeout) {
        AtomicBoolean cleanupDone = new AtomicBoolean(false);
        Runnable cleanupOnce = () -> {
            if (cleanupDone.compareAndSet(false, true)) {
                cleanup.run();
            }
        };

        completion.whenComplete((ignored, error) -> cleanupOnce.run());

        if (detachCleanupTimeout == null || detachCleanupTimeout.isNegative() || detachCleanupTimeout.isZero()) {
            return;
        }

        CompletableFuture.delayedExecutor(detachCleanupTimeout.toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
            if (cleanupDone.compareAndSet(false, true)) {
                LOGGER.warn("Forcing detached event runtime cleanup after timeout. timeout={}", detachCleanupTimeout);
                cleanup.run();
            }
        });
    }

    private void invokeRunStartedSafely(RunLifecycleExtension lifecycleExtension,
                                        ExecutionContext ctx,
                                        AssemblyRunTrace execution) {
        try {
            lifecycleExtension.onRunStarted(ctx, execution);
        } catch (Error error) {
            throw error;
        } catch (Exception e) {
            if (lifecycleExtension.failureMode() == LifecycleFailureMode.CRITICAL) {
                throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
            }

            LOGGER.error("A RunLifecycleExtension failed during onRunStarted. Ignoring. extension={}",
                         lifecycleExtension.getClass().getName(), e);
        }
    }

    private void invokeRunCompletedSafely(RunLifecycleExtension lifecycleExtension,
                                          ExecutionContext ctx,
                                          AssemblyRunTrace execution) {
        try {
            lifecycleExtension.onRunCompleted(ctx, execution);
        } catch (Error error) {
            throw error;
        } catch (Exception e) {
            if (lifecycleExtension.failureMode() == LifecycleFailureMode.CRITICAL) {
                throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
            }

            LOGGER.error("A RunLifecycleExtension failed during onRunCompleted. Ignoring. extension={}",
                         lifecycleExtension.getClass().getName(), e);
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
