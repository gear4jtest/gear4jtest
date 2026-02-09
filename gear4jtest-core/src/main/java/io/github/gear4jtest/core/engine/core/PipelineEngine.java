package io.github.gear4jtest.core.engine.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.engine.spi.PipelineExecutor;
import io.github.gear4jtest.core.engine.spi.RuntimeExtension;
import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.DefaultStationExecutionContext;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.ExecutionResult;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.model.StationKind;
import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.persistence.StationLog;

public class PipelineEngine implements PipelineExecutor {

    private final ResourceFactory resourceFactory;
    private final RunnerStackBuilder stackBuilder;

    public PipelineEngine(ResourceFactory resourceFactory, RunnerStackBuilder stackBuilder) {
        this.resourceFactory = resourceFactory;
        this.stackBuilder = stackBuilder;
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

        var executionId = UUID.randomUUID();
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
            StationExecutionContext rootContext = new DefaultStationExecutionContext(
                    "root-invoker",
                    StationKind.OTHER,
                    ctx,
                    null);

            // 4. Run !
            Object input = request.getInput();
            for (AbstractStation station : pipeline.getStations()) {
                StationLog stationLog = rootRunner.run(input, station, rootContext);
                input = stationLog.getOutput(null);

                if (stationLog.getStatus() == StationLog.Status.FAILED || stationLog.getStatus() == StationLog.Status.STOPPED) {
                    ctx.getPipelineExecution().setContext(ctx.getContext());
                    ctx.getPipelineExecution().setEndTime(Instant.now());
//                    if (configuration.getPersistence() != null
//                            && configuration.getPersistence().isStoreResultObject()) {
                        ctx.getPipelineExecution().setResult(null);
//                    }
                    ctx.getAssemblyRunManager().end(ctx.getPipelineExecution());
//                    success = false;
                    break;
                }
            }

            for (RuntimeExtension ext : request.getExtensions()) {
                ext.onSuccess(ctx, input);
            }

            return (ExecutionResult<OUT>) ExecutionResult.success(input, execution);
        } catch (Exception e) {
            for (RuntimeExtension ext : request.getExtensions()) {
                ext.onFailure(ctx, e);
            }

            return ExecutionResult.failure(e, ctx.getPipelineExecution());
        } finally {
//            ctx.closeAllResources();
            for (RuntimeExtension ext : request.getExtensions()) {
                ext.onEnd(ctx);
            }
        }
    }
}
