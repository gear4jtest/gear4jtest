package io.github.gear4jtest.core.model;

/**
 * Un processor qui intervient avant l'exécution de l'opération.
 * (Tu peux ajouter afterExecution plus tard si besoin.)
 */
public interface Processor {

	<I> void beforeExecution(I input, StationExecutionContext ctx) throws Exception;

	void afterExecution(Object result, StationExecutionContext context);
}