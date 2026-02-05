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
        // Le log a été créé par le ScopeInitializingRunner juste avant nous
        StationLog log = ctx.getRecord();

        // HOOK START : On sauvegarde l'état initial
        manager.append(log);

        try {
            // On continue la chaîne
            return delegate.run(input, station, ctx);

        } catch (Exception e) {
            log.markFailed(e);
            throw e;
        } finally {
            // HOOK END : On met à jour
            manager.append(log);
        }
    }
}