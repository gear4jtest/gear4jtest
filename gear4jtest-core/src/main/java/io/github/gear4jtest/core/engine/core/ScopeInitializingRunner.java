package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.DefaultStationExecutionContext;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.persistence.StationLog.Status;

public class ScopeInitializingRunner implements StationRunner {

    private final StationRunner delegate; // Le reste de la chaîne (Persist -> Terminal)

    public ScopeInitializingRunner(StationRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public StationLog run(Object input, AbstractStation station, StationExecutionContext parentCtx) {
        // 1. CRÉATION DU LOG (Single Source of Truth)
        // On utilise l'ID du parent fourni par le contexte appelant
        StationLog log = StationLog.start(
            parentCtx.getGlobalContext().getExecutionId().toString(),
            station.getId(),
            parentCtx.getGlobalContext().getCurrentParentOperationId()
//            parentCtx.getRecord() != null ? parentCtx.getRecord().getId() : null // Parent ID
        );
        log.setItemId(parentCtx.getGlobalContext().getCurrentItemId());
        log.setStatus(Status.RUNNING);

        // 2. CRÉATION DU CONTEXTE LOCAL (DefaultStationExecutionContext)
        // C'est ici qu'on encapsule le tout.
        StationExecutionContext currentCtx = new DefaultStationExecutionContext(
            station.getId(),
            station.getKind(),
            parentCtx.getGlobalContext(),
            log
        );
        parentCtx.getGlobalContext().pushParentOperationId(log.getId());

        // 3. PASSAGE AUX SUIVANTS
        // Les suivants (Persistance, Terminal) reçoivent un contexte TOUT PRÊT.
        // Ils n'ont rien à créer, juste à consommer.
        return delegate.run(input, station, currentCtx);
    }
}