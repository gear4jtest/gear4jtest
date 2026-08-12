package io.github.gear4jtest.core.engine.strategy;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ContainerExecutionAggregationTest {
    @Test
    void completed_shouldExposeResultsAndNoInterrupt() {
        // Given
        StationLogTrace child = successfulChild();
        List<StationLogTrace> results = List.of(child);
        List<Throwable> errors = List.of();

        // When
        ContainerExecutionAggregation aggregation = ContainerExecutionAggregation.completed(results, errors);

        // Then
        assertThat(aggregation.results()).isSameAs(results);
        assertThat(aggregation.collectedErrors()).isSameAs(errors);
        assertThat(aggregation.interruptingChild()).isEmpty();
    }

    @Test
    void interrupted_shouldRequireAndExposeInterruptingChild() {
        // Given
        StationLogTrace child = successfulChild();

        // When
        ContainerExecutionAggregation aggregation = ContainerExecutionAggregation.interrupted(List.of(child),
                                                                                              List.of(), child);

        // Then
        assertThat(aggregation.interruptingChild()).contains(child);
        assertThatNullPointerException()
                .isThrownBy(() -> ContainerExecutionAggregation.interrupted(List.of(child), List.of(), null))
                .withMessage("interruptingChild must not be null");
    }

    private static StationLogTrace successfulChild() {
        StationLogTrace child = StationLogTrace.start(UUID.randomUUID(), "branch", null);
        child.markSuccess("output");
        return child;
    }
}
