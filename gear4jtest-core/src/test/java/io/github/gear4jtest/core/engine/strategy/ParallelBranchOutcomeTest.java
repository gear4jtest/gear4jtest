package io.github.gear4jtest.core.engine.strategy;

import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParallelBranchOutcomeTest {
    @Test
    void pendingStates_shouldNotCarryTerminalLogs() {
        // When
        ParallelBranchOutcome notVisited = ParallelBranchOutcome.notVisited();
        ParallelBranchOutcome submitted = ParallelBranchOutcome.submitted();

        // Then
        assertThat(notVisited.state()).isEqualTo(ParallelBranchOutcome.State.NOT_VISITED);
        assertThat(submitted.state()).isEqualTo(ParallelBranchOutcome.State.SUBMITTED);
        assertThat(notVisited.log()).isEmpty();
        assertThat(submitted.log()).isEmpty();
        assertThat(notVisited.isTerminal()).isFalse();
        assertThatThrownBy(notVisited::requireLog).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_VISITED");
    }

    @Test
    void terminalState_shouldRequireAndExposeItsLog() {
        // Given
        StationLogTrace child = successfulChild();

        // When
        ParallelBranchOutcome outcome = ParallelBranchOutcome.terminal(ParallelBranchOutcome.State.COMPLETED, child);

        // Then
        assertThat(outcome.isTerminal()).isTrue();
        assertThat(outcome.log()).contains(child);
        assertThat(outcome.requireLog()).isSameAs(child);
    }

    @Test
    void stateAndLogPresence_shouldAlwaysAgree() {
        // Given
        StationLogTrace child = successfulChild();

        // Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParallelBranchOutcome(ParallelBranchOutcome.State.NOT_VISITED,
                        Optional.of(child)))
                .withMessageContaining("must not contain");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ParallelBranchOutcome(ParallelBranchOutcome.State.COMPLETED,
                        Optional.empty()))
                .withMessageContaining("must contain");
    }

    private static StationLogTrace successfulChild() {
        StationLogTrace child = StationLogTrace.start(UUID.randomUUID(), "branch", null);
        child.markSuccess("output");
        return child;
    }
}
