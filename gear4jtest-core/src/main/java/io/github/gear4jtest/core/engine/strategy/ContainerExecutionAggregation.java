package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;

/**
 * Internal result of branch execution before the container output is assembled.
 */
record ContainerExecutionAggregation(List<StationLogTrace> results,
                                     List<Throwable> collectedErrors,
                                     Optional<StationLogTrace> interruptingChild) {
    ContainerExecutionAggregation {
        results = Objects.requireNonNull(results, "results must not be null");
        collectedErrors = Objects.requireNonNull(collectedErrors, "collectedErrors must not be null");
        interruptingChild = Objects.requireNonNull(interruptingChild, "interruptingChild must not be null");
    }

    static ContainerExecutionAggregation completed(List<StationLogTrace> results,
                                                   List<Throwable> collectedErrors) {
        return new ContainerExecutionAggregation(results, collectedErrors, Optional.empty());
    }

    static ContainerExecutionAggregation interrupted(List<StationLogTrace> results,
                                                     List<Throwable> collectedErrors,
                                                     StationLogTrace interruptingChild) {
        return new ContainerExecutionAggregation(results, collectedErrors,
                Optional.of(Objects.requireNonNull(interruptingChild, "interruptingChild must not be null")));
    }
}
