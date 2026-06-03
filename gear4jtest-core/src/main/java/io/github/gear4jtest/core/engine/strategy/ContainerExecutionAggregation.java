package io.github.gear4jtest.core.engine.strategy;

import java.util.List;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;

/**
 * Internal result of branch execution before the container output is assembled.
 */
record ContainerExecutionAggregation(List<StationLogTrace> results,
                                     List<Throwable> collectedErrors,
                                     StationLogTrace interruptingChild) {}
