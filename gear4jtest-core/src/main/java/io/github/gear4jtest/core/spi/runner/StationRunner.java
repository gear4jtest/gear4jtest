package io.github.gear4jtest.core.spi.runner;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;

/**
 * Responsable de l'exécution d'une station unique. C'est le point d'extension
 * pour les Décorateurs (Log, DryRun, Retry...).
 */
@FunctionalInterface
public interface StationRunner {
    StationLogTrace run(Object input, AbstractStation<?, ?> station, StationExecutionContext ctx);
}
