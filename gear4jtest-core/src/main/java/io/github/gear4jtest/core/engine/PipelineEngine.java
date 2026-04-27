package io.github.gear4jtest.core.engine;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.PipelineExecutor;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.engine.support.ExecutorDecorator;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension.RunChain;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
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

    private PipelineEngine(Builder builder) {
        this.runnerChainFactory = Objects.requireNonNull(builder.runnerChainFactory, "ChainFactory must not be null");
        this.resourceFactory = Objects.requireNonNull(builder.resourceFactory, "ResourceFactory must not be null");
        this.extensionResolver = Objects.requireNonNull(builder.extensionResolver, "Extension resolver must not be null");
        this.executionContextRegistry =
                Objects.requireNonNull(builder.executionContextRegistry, "ExecutionContextRegistry must not be null");
        this.defaultIdGenerator = builder.idGenerator != null ? builder.idGenerator : IdGenerator.defaultGenerator();
        this.taskFactory = builder.taskFactory != null ? builder.taskFactory : new TaskFactory();
        this.payloadCloner = builder.payloadCloner != null ? builder.payloadCloner : PayloadCloners.immutableAware();
    }

    @Override
    public <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Starting pipeline execution. pipelineId={}, rootStation={}, requestExtensions={}",
                    pipeline.getId(),
                    pipeline.getRootStation() != null ? pipeline.getRootStation().getId() : null,
                    request.getExtensions().stream().map(e -> e.getClass().getSimpleName()).toList());
        }

        ResolvedExtensions resolvedExtensions = extensionResolver.resolve(pipeline, request);

        EventHandlingDefinition eventHandlingDefinition = Optional.ofNullable(pipeline.getConfiguration())
                .map(AssemblyLine.Configuration::getEventHandlingDefinition)
                .orElse(null);
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

        ExecutionContext.EventRuntimeOptions eventRuntimeOptions =
                ExecutionContext.EventRuntimeOptions.from(eventHandlingDefinition);

        IdGenerator effectiveGenerator = Optional.ofNullable(request.getIdGenerator()).orElse(this.defaultIdGenerator);

        var executionId = effectiveGenerator.generate();
        var execution = new AssemblyRunTrace(executionId, pipeline.getId(), new HashMap<>(effectiveContext));

        var effectiveResourceFactory = Optional.ofNullable(request.getResourceFactory())
                .or(() -> Optional.ofNullable(this.resourceFactory))
                .orElseThrow();

        ExecutionServices services = new ExecutionServices(eventManager, effectiveResourceFactory);

        var ctx = new ExecutionContext(
                executionId,
                pipeline.getId(),
                services,
                execution,
                eventRuntimeOptions);
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
            RunChain<IN, OUT> chain = () -> doExecuteInternal(pipeline, request, rootRunner, rootContext, ctx, execution);

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
                shutdownHandle.completion().whenComplete((ignored, error) -> cleanup.run());
            } else {
                cleanup.run();
            }
        }
    }

    private static <IN, OUT> ExecutionResult<OUT> doExecuteInternal(
            AssemblyLine<IN, OUT> pipeline,
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
            case STOPPED, CANCELLED -> {
                execution.setStatus(ExecutionStatus.STOPPED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.success(result, execution);
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

    private static void finalizeRunFromResult(
            ExecutionContext ctx,
            AssemblyRunTrace execution,
            ExecutionResult<?> result,
            Throwable fatalError) {

        if (execution.getEndTime() == null) {
            execution.setEndTime(Instant.now());
        }

        try {
            execution.setContext(new HashMap<>(ctx.getContext()));
        } catch (Throwable ignored) {
        }

        if (fatalError != null) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage("CRITICAL JVM ERROR: " + fatalError);
        } else if (result != null) {
            execution.setResult(result.getResult());
            if (!result.isSuccess()) {
                execution.setStatus(ExecutionStatus.FAILED);
                if (result.getError() != null) {
                    execution.setError(asException(result.getError()));
                }
            } else if (execution.getStatus() == null || execution.getStatus() == ExecutionStatus.RUNNING) {
                execution.setStatus(ExecutionStatus.SUCCEEDED);
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

    private void invokeRunStartedSafely(
            RunLifecycleExtension lifecycleExtension,
            ExecutionContext ctx,
            AssemblyRunTrace execution) {
        try {
            lifecycleExtension.onRunStarted(ctx, execution);
        } catch (Error error) {
            throw error;
        } catch (Exception e) {
            if (lifecycleExtension.failureMode() == LifecycleFailureMode.CRITICAL) {
                throw e instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new RuntimeException(e);
            }

            LOGGER.error(
                    "A RunLifecycleExtension failed during onRunStarted. Ignoring. extension={}",
                    lifecycleExtension.getClass().getName(),
                    e);
        }
    }

    private void invokeRunCompletedSafely(
            RunLifecycleExtension lifecycleExtension,
            ExecutionContext ctx,
            AssemblyRunTrace execution) {
        try {
            lifecycleExtension.onRunCompleted(ctx, execution);
        } catch (Error error) {
            throw error;
        } catch (Exception e) {
            if (lifecycleExtension.failureMode() == LifecycleFailureMode.CRITICAL) {
                throw e instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new RuntimeException(e);
            }

            LOGGER.error(
                    "A RunLifecycleExtension failed during onRunCompleted. Ignoring. extension={}",
                    lifecycleExtension.getClass().getName(),
                    e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ResourceFactory resourceFactory;
        private RunnerChainFactory runnerChainFactory;
        private RuntimeExtensionResolver extensionResolver;
        private ExecutionContextRegistry executionContextRegistry;
        private IdGenerator idGenerator;
        private TaskFactory taskFactory;
        private PayloadCloner payloadCloner;

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

        public PipelineEngine build() {
            return new PipelineEngine(this);
        }
    }
}
