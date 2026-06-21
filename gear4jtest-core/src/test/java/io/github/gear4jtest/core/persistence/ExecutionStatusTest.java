package io.github.gear4jtest.core.persistence;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionStatusTest {
    @Test
    void statusClassification_shouldExposeActiveAndTerminalFamilies() {
        for (ExecutionStatus status : List.of(ExecutionStatus.PENDING, ExecutionStatus.INITIALIZING,
                                              ExecutionStatus.RUNNING, ExecutionStatus.PAUSED)) {
            assertThat(status.isActive()).isTrue();
            assertThat(status.isTerminal()).isFalse();
        }
        for (ExecutionStatus status : List.of(ExecutionStatus.SUCCEEDED, ExecutionStatus.FAILED,
                                              ExecutionStatus.STOPPED, ExecutionStatus.CANCELLED,
                                              ExecutionStatus.SKIPPED)) {
            assertThat(status.isActive()).isFalse();
            assertThat(status.isTerminal()).isTrue();
        }
    }

    @Test
    void semanticPredicates_shouldMatchDedicatedTerminalStatuses() {
        assertThat(ExecutionStatus.SUCCEEDED.isSuccess()).isTrue();
        assertThat(ExecutionStatus.SKIPPED.isSkipped()).isTrue();
        assertThat(ExecutionStatus.STOPPED.isStopped()).isTrue();
        assertThat(ExecutionStatus.FAILED.isError()).isTrue();
        assertThat(ExecutionStatus.CANCELLED.isError()).isTrue();
        assertThat(ExecutionStatus.RUNNING.isSuccess()).isFalse();
        assertThat(ExecutionStatus.RUNNING.isSkipped()).isFalse();
        assertThat(ExecutionStatus.RUNNING.isStopped()).isFalse();
        assertThat(ExecutionStatus.RUNNING.isError()).isFalse();
    }
}
