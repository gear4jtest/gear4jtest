package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

public class PersistingStationRunner implements StationRunner {
    private final StationRunner delegate;
    private final AssemblyRunManager manager;

    public PersistingStationRunner(StationRunner delegate, AssemblyRunManager manager) {
        this.delegate = delegate;
        this.manager = manager;
    }

    @Override
    public StationLog run(Object input, AbstractStation station, StationExecutionContext ctx) {
        StationLog stationLog = ctx.getRecord();

        manager.append(stationLog);

        try {
            return delegate.run(input, station, ctx);
        } catch (Exception e) {
            stationLog.markFailed(e);
            throw e; // A revoir, on ne devrait pas throw
        } finally {
            manager.append(stationLog);
        }
    }
}