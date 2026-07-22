package io.github.gear4jtest.core.engine.runner;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.event.StationStartedEvent;
import io.github.gear4jtest.core.exception.StationLifecycleException;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StationLifecycleRunner implements StationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StationLifecycleRunner.class);
    private final StationRunner delegate;
    private final List<StationLifecycleExtension> lifecycleExtensions;

    public StationLifecycleRunner(StationRunner delegate, List<StationLifecycleExtension> lifecycleExtensions) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.lifecycleExtensions = lifecycleExtensions == null ? List.of() : List.copyOf(lifecycleExtensions);
    }

    @Override
    public StationLogTrace run(Object input, AbstractStation<?, ?> station, StationExecutionContext ctx) {
        ExecutionContext runCtx = ctx.getGlobalContext();
        StationLogRecord startedSnapshot = StationLogRecord.from(EngineStationContexts.trace(ctx));

        publishStartedEvent(runCtx, ctx, input);

        boolean criticalStartFailure = false;
        for (StationLifecycleExtension extension : lifecycleExtensions) {
            criticalStartFailure |= invokeStartedSafely(extension, runCtx, ctx, startedSnapshot);
        }

        StationLogTrace result = criticalStartFailure ? EngineStationContexts.trace(ctx)
                : EngineStationContexts.mutableTrace(delegate.run(input, station, ctx));

        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeCompletedSafely(extension, runCtx, ctx, result);
        }

        // Completion hooks may change a successful/skipped station into a failed
        // station
        // when a CRITICAL observer fails. Publish only the normalized final status.
        publishFinishedEvent(runCtx, ctx, input, result);

        return result;
    }

    private void publishStartedEvent(ExecutionContext runCtx, StationExecutionContext stationCtx, Object input) {
        if (runCtx.getServices().getEventManager() == null || EngineStationContexts.trace(stationCtx) == null) {
            return;
        }
        StationLogTrace stationLog = EngineStationContexts.trace(stationCtx);
        runCtx.getServices().getEventManager()
                .publish(new StationStartedEvent(runCtx.getAssemblyLineId(), runCtx.getExecutionId(),
                        stationLog.getId(),
                        stationCtx.getOperationId(), stationLog.getParentOperationId(), stationLog.getBranchId(),
                        stationLog.getItemId(), runCtx.getEventRuntimeOptions().getEventPayloadPolicy()
                                .mapStationInput(input, stationCtx)));
    }

    private void publishFinishedEvent(ExecutionContext runCtx,
                                      StationExecutionContext stationCtx,
                                      Object input,
                                      StationLogTrace result) {
        if (runCtx.getServices().getEventManager() == null || result == null) {
            return;
        }
        runCtx.getServices().getEventManager()
                .publish(new StationFinishedEvent(runCtx.getAssemblyLineId(), runCtx.getExecutionId(), result.getId(),
                        stationCtx.getOperationId(), result.getParentOperationId(), result.getBranchId(),
                        result.getItemId(), runCtx.getEventRuntimeOptions().getEventPayloadPolicy()
                                .mapStationInput(input, stationCtx),
                        result.getStatus(), runCtx.getEventRuntimeOptions().getEventPayloadPolicy()
                                .mapStationOutput(result.getOutput(), stationCtx),
                        extractPrimaryError(result)));
    }

    private Exception extractPrimaryError(StationLogTrace result) {
        if (result.getThrowables() == null || result.getThrowables().isEmpty()) {
            return null;
        }
        Throwable throwable = result.getThrowables().get(0);
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

    private boolean invokeStartedSafely(StationLifecycleExtension extension,
                                        ExecutionContext runCtx,
                                        StationExecutionContext stationCtx,
                                        StationLogRecord snapshot) {
        try {
            extension.onStationStarted(runCtx, stationCtx, snapshot);
            return false;
        } catch (Exception exception) {
            return handleLifecycleFailure(extension, EngineStationContexts.trace(stationCtx), snapshot.operationId(),
                                          "onStationStarted", exception);
        }
    }

    private void invokeCompletedSafely(StationLifecycleExtension extension,
                                       ExecutionContext runCtx,
                                       StationExecutionContext stationCtx,
                                       StationLogTrace result) {
        try {
            extension.onStationCompleted(runCtx, stationCtx, StationLogRecord.from(result));
        } catch (Exception exception) {
            handleLifecycleFailure(extension, result, result.getOperationId(), "onStationCompleted", exception);
        }
    }

    /**
     * Records lifecycle observer failures on the station itself instead of leaking
     * a raw infrastructure exception out of the station runner chain. A parent
     * strategy can consequently apply its usual {@code FlowConfig} to the failed
     * child log.
     */
    private boolean handleLifecycleFailure(StationLifecycleExtension extension,
                                           StationLogTrace stationLog,
                                           String operationId,
                                           String lifecycleCallback,
                                           Exception exception) {
        LOGGER.error("StationLifecycleExtension failed during {}. extension={}, stationId={}", lifecycleCallback,
                     extension.getClass().getName(), operationId, exception);
        if (extension.failureMode() != LifecycleFailureMode.CRITICAL) {
            return false;
        }

        StationLifecycleException lifecycleFailure = new StationLifecycleException(lifecycleCallback,
                extension.getClass(), exception);
        StationLogStatus status = stationLog.getStatus();
        if (status == StationLogStatus.RUNNING || status == StationLogStatus.SUCCEEDED
                || status == StationLogStatus.SKIPPED) {
            stationLog.markFailed(lifecycleFailure);
        } else {
            // Do not erase an earlier FAILED/STOPPED/CANCELLED terminal outcome.
            // Retain the lifecycle failure as additional diagnostic material.
            stationLog.addErrorHandlerException(lifecycleFailure);
        }
        return true;
    }
}
