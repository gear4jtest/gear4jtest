package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.model.AbstractStation;
import io.github.gear4jtest.core.model.StationExecutionContext;
import io.github.gear4jtest.core.persistence.StationLog;

/**
 * Responsable de l'exécution d'une station unique.
 * C'est le point d'extension pour les Décorateurs (Log, DryRun, Retry...).
 */
@FunctionalInterface
public interface StationRunner {
    StationLog run(Object input, AbstractStation station, StationExecutionContext ctx);
}
