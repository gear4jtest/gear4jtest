package io.github.gear4jtest.core.api.behavior;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

/**
 * Un processor qui intervient avant l'exécution de l'opération.
 * (Tu peux ajouter afterExecution plus tard si besoin.)
 */
public interface Processor {

	<I> void beforeExecution(I input, StationExecutionContext ctx) throws Exception;

	void afterExecution(Object result, StationExecutionContext context);
}