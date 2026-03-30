package io.github.gear4jtest.core.engine.runner;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.persistence.StationLogSnapshot;
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
    public StationLog run(Object input, AbstractStation station, StationExecutionContext ctx) {
        ExecutionContext runCtx = ctx.getGlobalContext();
        StationLogSnapshot startedSnapshot = StationLogSnapshot.from(ctx.getRecord());

        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeStartedSafely(extension, runCtx, ctx, startedSnapshot);
        }

        StationLog result = delegate.run(input, station, ctx);
        StationLogSnapshot completedSnapshot = StationLogSnapshot.from(result);

        for (StationLifecycleExtension extension : lifecycleExtensions) {
            invokeCompletedSafely(extension, runCtx, ctx, completedSnapshot);
        }

        return result;
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
