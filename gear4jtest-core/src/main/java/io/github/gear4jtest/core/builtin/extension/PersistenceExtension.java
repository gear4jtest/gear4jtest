package io.github.gear4jtest.core.builtin.extension;

import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;

/**
 * Persistence extension responsible for run and station durability.
 *
 * <p>
 * If this extension fails, the failure is considered critical.
 * </p>
 *
 * <p>
 * Every lifecycle snapshot is emitted exactly once to the configured manager.
 * The manager alone owns buffering, batching and flush scheduling. Before a run
 * is ended, the extension asks the manager to flush any remaining run-local
 * records.
 * </p>
 */
public class PersistenceExtension implements RunLifecycleExtension, StationLifecycleExtension {
    private final RunPersistenceManager manager;

    public PersistenceExtension(RunPersistenceManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager must not be null");
    }

    /**
     * Persistence observes the normalized terminal state after ordinary lifecycle
     * observers had an opportunity to affect the station outcome.
     *
     * <p>
     * If another critical completion observer fails, its {@code FAILED} status must
     * be present in the snapshot appended by this extension. A persistence failure
     * itself cannot, by definition, durably record its own failure.
     * </p>
     */
    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    @Override
    public LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.CRITICAL;
    }

    @Override
    public void onRunStarted(ExecutionContext ctx, RunTrace run) {
        manager.start(run, ctx == null ? null : ctx.getPersistenceConfiguration());
    }

    @Override
    public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
        manager.flush(run.getId());
        manager.end(run);
    }

    @Override
    public void onStationStarted(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot) {
        manager.append(snapshot);
    }

    @Override
    public void onStationCompleted(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot) {
        manager.append(snapshot);
    }

    @Override
    public void onStationSkipped(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot,
                                 StationSkipReason reason) {
        manager.append(snapshot);
    }

    @Override
    public void onStationCancelled(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot,
                                   StationCancellationReason reason,
                                   Exception error) {
        manager.append(snapshot);
    }

    @Override
    public void onStationInterrupted(ExecutionContext runCtx,
                                     StationExecutionContext stationCtx,
                                     StationLogRecord snapshot,
                                     StationInterruptionReason reason,
                                     String interruptingOperationId,
                                     Exception error) {
        manager.append(snapshot);
    }

    @Override
    public void onStationFailedBeforeStart(ExecutionContext runCtx,
                                           StationExecutionContext stationCtx,
                                           StationLogRecord snapshot,
                                           Exception error) {
        manager.append(snapshot);
    }
}
