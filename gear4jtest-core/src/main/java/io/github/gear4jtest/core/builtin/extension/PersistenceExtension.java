package io.github.gear4jtest.core.builtin.extension;

import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
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
 */
public class PersistenceExtension implements RunLifecycleExtension, StationLifecycleExtension {
    private final AssemblyRunManager manager;

    public PersistenceExtension(AssemblyRunManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager must not be null");
    }

    @Override
    public LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.CRITICAL;
    }

    @Override
    public void onRunStarted(ExecutionContext ctx, AssemblyRunTrace run) {
        manager.start(run);
    }

    @Override
    public void onRunCompleted(ExecutionContext ctx, AssemblyRunTrace run) {
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
}
