package io.github.gear4jtest.core.engine.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.engine.spi.PipelineExecutor;
import io.github.gear4jtest.core.engine.spi.RuntimeExtension;
import io.github.gear4jtest.core.engine.spi.StationRunner;
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

public class PipelineEngine implements PipelineExecutor {

    private final ResourceFactory resourceFactory;
    private final RunnerStackBuilder stackBuilder;
    private final IdGenerator defaultIdGenerator;

    private PipelineEngine(Builder builder) {
        this.stackBuilder = Objects.requireNonNull(builder.stackBuilder, "StackBuilder must not be null");
        this.resourceFactory = Objects.requireNonNull(builder.resourceFactory, "ResourceFactory must not be null");
        this.defaultIdGenerator = builder.idGenerator != null ? builder.idGenerator : IdGenerator.defaultGenerator();
    }

    @Override
    public <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest request) {
        // 1. Context Init
        var eventManager = new EventManager(
                Optional.ofNullable(pipeline.getConfiguration().getEventHandlingDefinition())
                        .map(EventHandlingDefinition::getEventBuses)
                        .orElse(List.of())
        );

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

        try {
            for (RuntimeExtension ext : request.getExtensions()) {
                ext.prepare(ctx, request);
            }

            // 2. Build Stack (C'est ici que PersistenceFeature -> Extension -> Manager injecté)
            StationRunner rootRunner = stackBuilder.build(pipeline, request, ctx);

            for (RuntimeExtension ext : request.getExtensions()) {
                ext.onStart(ctx);
            }

            // 3. Dummy Root Context (Bootstrapping)
            StationExecutionContext rootContext = new DefaultStationExecutionContext("root-invoker", ctx);

            // 4. Run root station (Composite) : les stratégies gèrent bubbling + stop/failure.
            StationLog rootLog = rootRunner.run(request.getInput(), pipeline.getRootStation(), rootContext);
            Object result = rootLog.getOutput();

            // 5. Finalise AssemblyRun (status / result)
            ctx.getPipelineExecution().setContext(ctx.getContext());
            ctx.getPipelineExecution().setEndTime(Instant.now());

            return switch (rootLog.getStatus()) {
                case SUCCEEDED, SKIPPED -> {
                    ctx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.SUCCEEDED);
                    ctx.getPipelineExecution().setResult(result);

                    for (RuntimeExtension ext : request.getExtensions()) {
                        ext.onSuccess(ctx, result);
                    }
                    yield (ExecutionResult<OUT>) ExecutionResult.success(result, execution);
                }
                case STOPPED, CANCELLED -> {
                    ctx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.STOPPED);
                    ctx.getPipelineExecution().setResult(result);

                    // STOP/CANCEL ne sont pas des exceptions : on considère que le run s'est terminé "proprement".
                    for (RuntimeExtension ext : request.getExtensions()) {
                        ext.onSuccess(ctx, result);
                    }
                    yield (ExecutionResult<OUT>) ExecutionResult.success(result, execution);
                }
                case FAILED, RUNNING -> {
                    Exception failure = new RuntimeException(
                            rootLog.getErrorMessage() != null ? rootLog.getErrorMessage() : "Pipeline failed");
                    ctx.getPipelineExecution().setStatus(io.github.gear4jtest.core.persistence.ExecutionStatus.FAILED);
                    ctx.getPipelineExecution().setError(failure);

                    for (RuntimeExtension ext : request.getExtensions()) {
                        ext.onFailure(ctx, failure);
                    }
                    yield ExecutionResult.failure(failure, ctx.getPipelineExecution());
                }
            };
        } catch (Exception e) {
            e.printStackTrace();
            for (RuntimeExtension ext : request.getExtensions()) {
                ext.onFailure(ctx, e);
            }

            return ExecutionResult.failure(e, ctx.getPipelineExecution());
        } finally {
            for (RuntimeExtension ext : request.getExtensions()) {
                ext.onEnd(ctx);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private RunnerStackBuilder stackBuilder;
        private ResourceFactory resourceFactory;
        private IdGenerator idGenerator;

        public Builder stackBuilder(RunnerStackBuilder stackBuilder) {
            this.stackBuilder = stackBuilder;
            return this;
        }

        public Builder resourceFactory(ResourceFactory resourceFactory) {
            this.resourceFactory = resourceFactory;
            return this;
        }

        public Builder idGenerator(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
            return this;
        }

        public PipelineEngine build() {
            return new PipelineEngine(this);
        }
    }
}
