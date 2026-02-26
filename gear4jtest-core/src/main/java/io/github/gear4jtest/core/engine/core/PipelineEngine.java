package io.github.gear4jtest.core.engine.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.engine.spi.PipelineExecutor;
import io.github.gear4jtest.core.engine.spi.RunInterceptorExtension;
import io.github.gear4jtest.core.engine.spi.RunInterceptorExtension.RunChain;
import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.engine.support.ExecutorDecorator;
import io.github.gear4jtest.core.engine.support.TaskFactory;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.DefaultStationExecutionContext;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.ExecutionResult;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.spi.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.github.gear4jtest.core.persistence.ExecutionStatus.FAILED;

public class PipelineEngine implements PipelineExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineEngine.class);

    private final ResourceFactory resourceFactory;
    private final RunnerStackBuilder stackBuilder;
    private final ExtensionRegistry globalExtensions;
    private final IdGenerator defaultIdGenerator;
    private final TaskFactory taskFactory;

    private PipelineEngine(Builder builder) {
        this.stackBuilder = Objects.requireNonNull(builder.stackBuilder, "StackBuilder must not be null");
        this.resourceFactory = Objects.requireNonNull(builder.resourceFactory, "ResourceFactory must not be null");
        this.globalExtensions = Objects.requireNonNull(builder.globalExtensions, "Global extension registry must not be null");
        this.defaultIdGenerator = builder.idGenerator != null ? builder.idGenerator : IdGenerator.defaultGenerator();
        this.taskFactory = builder.taskFactory != null ? builder.taskFactory : new TaskFactory();
    }

    @Override
    public <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Starting pipeline execution. pipelineId={}, rootStation={}, requestExtensions={} ",
                    pipeline.getId(),
                    pipeline.getRootStation() != null ? pipeline.getRootStation().getId() : null,
                    request.getExtensions().stream().map(e -> e.getClass().getSimpleName()).toList());
        }

        RunPlan plan = RunPlanFactory.create(globalExtensions, request);

        // 1. Context Init
        var eventManager = new EventManager(
                Optional.ofNullable(pipeline.getConfiguration().getEventHandlingDefinition())
                        .map(EventHandlingDefinition::getEventBuses)
                        .orElse(List.of())
        );

        ExecutorDecorator decorator = (rawExec, context) -> {
            ExecutorService wrapped = rawExec;
            for (var wrapperExt : plan.executorWrappers()) {
                wrapped = wrapperExt.wrapExecutor(wrapped, context);
            }
            return wrapped;
        };

        // 2. On instancie la boîte à outils technique
        ExecutionSupport support = new ExecutionSupport(decorator, taskFactory);

        Map<String, Object> effectiveContext = new HashMap<>(pipeline.getDefaultContext());
        if (request.getContext() != null) {
            effectiveContext.putAll(request.getContext());
        }

        IdGenerator effectiveGenerator = Optional.ofNullable(request.getIdGenerator()).orElse(this.defaultIdGenerator);

        // 2. Génération de l'ID du Run
        var executionId = effectiveGenerator.generate();
        var execution = new AssemblyRun(executionId, pipeline.getId(), new HashMap<>(effectiveContext));

        var resourceFactory = Optional.ofNullable(request.getResourceFactory())
                .or(() -> Optional.ofNullable(this.resourceFactory))
                .orElseThrow();
        var ctx = new ExecutionContext(
                executionId,
                pipeline.getId(),
                eventManager,
                resourceFactory,
                execution);

        // 2. Build Stack (C'est ici que PersistenceFeature -> Extension -> Manager injecté)
        StationRunner rootRunner = stackBuilder.build(pipeline, request, ctx, plan);

        // 3. Dummy Root Context (Bootstrapping)
        StationExecutionContext rootContext = new DefaultStationExecutionContext("root-invoker", ctx, support);

        // 4. Run root station (Composite) : les stratégies gèrent bubbling + stop/failure.
        List<RunInterceptorExtension> interceptors = plan.runInterceptors();
        RunChain<IN, OUT> chain = () -> doExecuteInternal(pipeline, request, rootRunner, rootContext, ctx, execution);

        // Boucle inversée : l'ordre 0 (le plus externe) enveloppera tout le reste
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            RunInterceptorExtension interceptor = interceptors.get(i);
            RunChain<IN, OUT> next = chain;
            chain = () -> interceptor.aroundRun(pipeline, request, ctx, next);
        }

        try {
            return chain.proceed();
        } catch (Exception e) {
            LOGGER.error("Error while executing pipeline", e);
            execution.setStatus(FAILED);
            execution.setError(e);
            return ExecutionResult.failure(e, execution);
        } finally {
            execution.setContext(ctx.getContext());
            execution.setEndTime(Instant.now());
        }
    }

    private static <IN, OUT> ExecutionResult<OUT> doExecuteInternal(AssemblyLine<IN, OUT> pipeline, RunRequest request, StationRunner rootRunner, StationExecutionContext rootContext, ExecutionContext ctx, AssemblyRun execution) {
        StationLog rootLog = rootRunner.run(request.getInput(), pipeline.getRootStation(), rootContext);
        Object result = rootLog.getOutput();

        return switch (rootLog.getStatus()) {
            case SUCCEEDED, SKIPPED -> {
                execution.setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.success(result, execution);
            }
            case STOPPED, CANCELLED -> {
                execution.setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.STOPPED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.success(result, execution);
            }
            case FAILED, RUNNING -> {
                Exception failure = new RuntimeException(
                        rootLog.getErrorMessage() != null ? rootLog.getErrorMessage() : "Pipeline failed");
                execution.setStatus(FAILED);
                execution.setError(failure);
                yield ExecutionResult.failure(failure, execution);
            }
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private RunnerStackBuilder stackBuilder;
        private ResourceFactory resourceFactory;
        private ExtensionRegistry globalExtensions;
        private IdGenerator idGenerator;
        private TaskFactory taskFactory;

        public Builder stackBuilder(RunnerStackBuilder stackBuilder) {
            this.stackBuilder = stackBuilder;
            return this;
        }

        public Builder resourceFactory(ResourceFactory resourceFactory) {
            this.resourceFactory = resourceFactory;
            return this;
        }
        public Builder globalExtensions(ExtensionRegistry globalExtensions) {
            this.globalExtensions = globalExtensions;
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

        public PipelineEngine build() {
            return new PipelineEngine(this);
        }
    }
}
