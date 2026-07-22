package io.github.gear4jtest.core.engine.runner;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.engine.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationCancelledEvent;
import io.github.gear4jtest.core.event.StationFailedBeforeStartEvent;
import io.github.gear4jtest.core.event.StationInterruptedEvent;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.event.StationSkippedEvent;
import io.github.gear4jtest.core.exception.StationLifecycleException;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records lifecycle and events for branch logs that did not go through the
 * ordinary station runner chain.
 */
public final class SyntheticStationLifecycleRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger(SyntheticStationLifecycleRecorder.class);
    private final List<StationLifecycleExtension> lifecycleExtensions;

    public SyntheticStationLifecycleRecorder(List<StationLifecycleExtension> lifecycleExtensions) {
        this.lifecycleExtensions = lifecycleExtensions == null ? List.of() : List.copyOf(lifecycleExtensions);
    }

    public StationLogTrace recordSkipped(StationExecutionContext parentContext,
                                         AbstractStation<?, ?> station,
                                         StationLogTrace log,
                                         Object input,
                                         StationSkipReason reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        StationExecutionContext syntheticContext = syntheticContext(parentContext, station, log);
        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeSkippedSafely(extension, parentContext.getGlobalContext(), syntheticContext,
                                StationLogRecord.from(log),
                                reason);
        }
        publishSkipped(parentContext.getGlobalContext(), syntheticContext, log, input, reason);
        return log;
    }

    public StationLogTrace recordCancelled(StationExecutionContext parentContext,
                                           AbstractStation<?, ?> station,
                                           StationLogTrace log,
                                           Object input,
                                           StationCancellationReason reason,
                                           Exception error) {
        Objects.requireNonNull(reason, "reason must not be null");
        StationExecutionContext syntheticContext = syntheticContext(parentContext, station, log);
        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeCancelledSafely(extension, parentContext.getGlobalContext(), syntheticContext,
                                  StationLogRecord.from(log), reason, error);
        }
        publishCancelled(parentContext.getGlobalContext(), syntheticContext, log, input, reason, error);
        return log;
    }

    public StationLogTrace recordInterrupted(StationExecutionContext parentContext,
                                             AbstractStation<?, ?> station,
                                             StationLogTrace log,
                                             Object input,
                                             StationInterruptionReason reason,
                                             String interruptingOperationId,
                                             Exception error) {
        Objects.requireNonNull(reason, "reason must not be null");
        StationExecutionContext syntheticContext = syntheticContext(parentContext, station, log);
        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeInterruptedSafely(extension, parentContext.getGlobalContext(), syntheticContext,
                                    StationLogRecord.from(log), reason, interruptingOperationId, error);
        }
        publishInterrupted(parentContext.getGlobalContext(), syntheticContext, log, input, reason,
                           interruptingOperationId, error);
        return log;
    }

    public StationLogTrace recordFailedBeforeStart(StationExecutionContext parentContext,
                                                   AbstractStation<?, ?> station,
                                                   StationLogTrace log,
                                                   Object input,
                                                   Exception error) {
        StationExecutionContext syntheticContext = syntheticContext(parentContext, station, log);
        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeFailedBeforeStartSafely(extension, parentContext.getGlobalContext(), syntheticContext,
                                          StationLogRecord.from(log), error);
        }
        publishFailedBeforeStart(parentContext.getGlobalContext(), syntheticContext, log, input, error);
        return log;
    }

    private StationExecutionContext syntheticContext(StationExecutionContext parentContext,
                                                     AbstractStation<?, ?> station,
                                                     StationLogTrace log) {
        return new DefaultStationExecutionContext(log.getOperationId(), station.getKind(), parentContext
                .getGlobalContext(), log, EngineStationContexts.support(parentContext));
    }

    private void publishSkipped(ExecutionContext runCtx,
                                StationExecutionContext stationCtx,
                                StationLogTrace log,
                                Object input,
                                StationSkipReason reason) {
        if (runCtx.getServices().getEventManager() == null) {
            return;
        }
        runCtx.getServices().getEventManager()
                .publish(new StationSkippedEvent(runCtx.getAssemblyLineId(), runCtx.getExecutionId(), log.getId(),
                        stationCtx.getOperationId(), log.getParentOperationId(), log.getBranchId(), log.getItemId(),
                        runCtx.getEventRuntimeOptions().getEventPayloadPolicy().mapStationInput(input, stationCtx),
                        reason));
    }

    private void publishCancelled(ExecutionContext runCtx,
                                  StationExecutionContext stationCtx,
                                  StationLogTrace log,
                                  Object input,
                                  StationCancellationReason reason,
                                  Exception error) {
        if (runCtx.getServices().getEventManager() == null) {
            return;
        }
        runCtx.getServices().getEventManager()
                .publish(new StationCancelledEvent(runCtx.getAssemblyLineId(), runCtx.getExecutionId(), log.getId(),
                        stationCtx.getOperationId(), log.getParentOperationId(), log.getBranchId(), log.getItemId(),
                        runCtx.getEventRuntimeOptions().getEventPayloadPolicy().mapStationInput(input, stationCtx),
                        reason, error));
    }

    private void publishInterrupted(ExecutionContext runCtx,
                                    StationExecutionContext stationCtx,
                                    StationLogTrace log,
                                    Object input,
                                    StationInterruptionReason reason,
                                    String interruptingOperationId,
                                    Exception error) {
        if (runCtx.getServices().getEventManager() == null) {
            return;
        }
        runCtx.getServices().getEventManager()
                .publish(new StationInterruptedEvent(runCtx.getAssemblyLineId(), runCtx.getExecutionId(), log.getId(),
                        stationCtx.getOperationId(), log.getParentOperationId(), log.getBranchId(), log.getItemId(),
                        runCtx.getEventRuntimeOptions().getEventPayloadPolicy().mapStationInput(input, stationCtx),
                        reason, interruptingOperationId, error));
    }

    private void publishFailedBeforeStart(ExecutionContext runCtx,
                                          StationExecutionContext stationCtx,
                                          StationLogTrace log,
                                          Object input,
                                          Exception error) {
        if (runCtx.getServices().getEventManager() == null) {
            return;
        }
        runCtx.getServices().getEventManager()
                .publish(new StationFailedBeforeStartEvent(runCtx.getAssemblyLineId(), runCtx.getExecutionId(),
                        log.getId(), stationCtx.getOperationId(), log.getParentOperationId(), log.getBranchId(),
                        log.getItemId(), runCtx.getEventRuntimeOptions().getEventPayloadPolicy()
                                .mapStationInput(input, stationCtx),
                        error));
    }

    private void invokeSkippedSafely(StationLifecycleExtension extension,
                                     ExecutionContext runCtx,
                                     StationExecutionContext stationCtx,
                                     StationLogRecord snapshot,
                                     StationSkipReason reason) {
        try {
            extension.onStationSkipped(runCtx, stationCtx, snapshot, reason);
        } catch (Exception exception) {
            handleLifecycleFailure(extension, EngineStationContexts.trace(stationCtx), snapshot.operationId(),
                                   "onStationSkipped",
                                   exception);
        }
    }

    private void invokeCancelledSafely(StationLifecycleExtension extension,
                                       ExecutionContext runCtx,
                                       StationExecutionContext stationCtx,
                                       StationLogRecord snapshot,
                                       StationCancellationReason reason,
                                       Exception error) {
        try {
            extension.onStationCancelled(runCtx, stationCtx, snapshot, reason, error);
        } catch (Exception exception) {
            handleLifecycleFailure(extension, EngineStationContexts.trace(stationCtx), snapshot.operationId(),
                                   "onStationCancelled",
                                   exception);
        }
    }

    private void invokeInterruptedSafely(StationLifecycleExtension extension,
                                         ExecutionContext runCtx,
                                         StationExecutionContext stationCtx,
                                         StationLogRecord snapshot,
                                         StationInterruptionReason reason,
                                         String interruptingOperationId,
                                         Exception error) {
        try {
            extension.onStationInterrupted(runCtx, stationCtx, snapshot, reason, interruptingOperationId, error);
        } catch (Exception exception) {
            handleLifecycleFailure(extension, EngineStationContexts.trace(stationCtx), snapshot.operationId(),
                                   "onStationInterrupted",
                                   exception);
        }
    }

    private void invokeFailedBeforeStartSafely(StationLifecycleExtension extension,
                                               ExecutionContext runCtx,
                                               StationExecutionContext stationCtx,
                                               StationLogRecord snapshot,
                                               Exception error) {
        try {
            extension.onStationFailedBeforeStart(runCtx, stationCtx, snapshot, error);
        } catch (Exception exception) {
            handleLifecycleFailure(extension, EngineStationContexts.trace(stationCtx), snapshot.operationId(),
                                   "onStationFailedBeforeStart", exception);
        }
    }

    private void handleLifecycleFailure(StationLifecycleExtension extension,
                                        StationLogTrace stationLog,
                                        String operationId,
                                        String lifecycleCallback,
                                        Exception exception) {
        LOGGER.error("StationLifecycleExtension failed during {}. extension={}, stationId={}", lifecycleCallback,
                     extension.getClass().getName(), operationId, exception);
        if (extension.failureMode() != LifecycleFailureMode.CRITICAL) {
            return;
        }

        StationLifecycleException lifecycleFailure = new StationLifecycleException(lifecycleCallback,
                extension.getClass(), exception);
        StationLogStatus status = stationLog.getStatus();
        if (status == StationLogStatus.RUNNING || status == StationLogStatus.SUCCEEDED
                || status == StationLogStatus.SKIPPED) {
            stationLog.markFailed(lifecycleFailure);
        } else {
            stationLog.addErrorHandlerException(lifecycleFailure);
        }
    }
}
