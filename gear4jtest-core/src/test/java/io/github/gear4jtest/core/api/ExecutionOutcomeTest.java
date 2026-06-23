package io.github.gear4jtest.core.api;

import io.github.gear4jtest.core.persistence.ExecutionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ExecutionOutcomeTest {
    @Test
    void executionOutcome_shouldMapToAndFromTerminalExecutionStatus() {
        for (ExecutionOutcome outcome : ExecutionOutcome.values()) {
            assertThat(ExecutionOutcome.fromExecutionStatus(outcome.toExecutionStatus())).isSameAs(outcome);
        }
    }

    @Test
    void fromExecutionStatus_shouldRejectActiveStatuses() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ExecutionOutcome.fromExecutionStatus(ExecutionStatus.RUNNING))
                .withMessageContaining("not terminal");
    }
}
