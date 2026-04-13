package io.github.gear4jtest.core.engine.runner;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.event.StationStartedEvent;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.persistence.StationLogSnapshot;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import java.util.List;
import java.util.Objects;
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
    public StationLog run(Object input, AbstractStation station, StationExecutionContext ctx) {
        ExecutionContext runCtx = ctx.getGlobalContext();
        StationLogSnapshot startedSnapshot = StationLogSnapshot.from(ctx.getRecord());

        publishStartedEvent(runCtx, ctx, input);

        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeStartedSafely(extension, runCtx, ctx, startedSnapshot);
        }

        StationLog result = delegate.run(input, station, ctx);
        StationLogSnapshot completedSnapshot = StationLogSnapshot.from(result);

        publishFinishedEvent(runCtx, ctx, input, result);

        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeCompletedSafely(extension, runCtx, ctx, completedSnapshot);
        }

        return result;
    }

    private void publishStartedEvent(ExecutionContext runCtx, StationExecutionContext stationCtx, Object input) {
        if (runCtx.getEventManager() == null || stationCtx.getRecord() == null) {
            return;
        }
        StationLog record = stationCtx.getRecord();
        runCtx.getEventManager().publish(new StationStartedEvent(
                runCtx.getPipelineId(),
                runCtx.getExecutionId(),
                record.getId(),
                stationCtx.getOperationId(),
                record.getParentOperationId(),
                record.getItemId(),
                input));
    }

    private void publishFinishedEvent(
            ExecutionContext runCtx,
            StationExecutionContext stationCtx,
            Object input,
            StationLog result) {
        if (runCtx.getEventManager() == null || result == null) {
            return;
        }
        runCtx.getEventManager().publish(new StationFinishedEvent(
                runCtx.getPipelineId(),
                runCtx.getExecutionId(),
                result.getId(),
                stationCtx.getOperationId(),
                result.getParentOperationId(),
                result.getItemId(),
                input,
                result.getStatus(),
                result.getOutput(),
                extractPrimaryError(result)));
    }

    private Exception extractPrimaryError(StationLog result) {
        if (result.getThrowables() == null || result.getThrowables().isEmpty()) {
            return null;
        }
        Throwable throwable = result.getThrowables().get(0);
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }

    private void invokeStartedSafely(
            StationLifecycleExtension extension,
            ExecutionContext runCtx,
            StationExecutionContext stationCtx,
            StationLogSnapshot snapshot) {
        try {
            extension.onStationStarted(runCtx, stationCtx, snapshot);
        } catch (Error error) {
            throw error;
        } catch (Exception exception) {
            LOGGER.error(
                    "StationLifecycleExtension failed during onStationStarted. extension={}, stationId={}",
                    extension.getClass().getName(),
                    snapshot.operationId(),
                    exception);
        }
    }

    private void invokeCompletedSafely(
            StationLifecycleExtension extension,
            ExecutionContext runCtx,
            StationExecutionContext stationCtx,
            StationLogSnapshot snapshot) {
        try {
            extension.onStationCompleted(runCtx, stationCtx, snapshot);
        } catch (Error error) {
            throw error;
        } catch (Exception exception) {
            LOGGER.error(
                    "StationLifecycleExtension failed during onStationCompleted. extension={}, stationId={}",
                    extension.getClass().getName(),
                    snapshot.operationId(),
                    exception);
        }
    }
}
