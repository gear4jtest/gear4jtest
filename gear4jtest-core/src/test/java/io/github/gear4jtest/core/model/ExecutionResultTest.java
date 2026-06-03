package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.api.ExecutionOutcome;
import io.github.gear4jtest.core.api.ExecutionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionResultTest {
    @Test
    void success_shouldExposeCompletedOutcome() {
        // Given / When
        ExecutionResult<String> executionResult = ExecutionResult.success("ok", null);

        // Then
        assertThat(executionResult.getResult()).isEqualTo("ok");
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.SUCCEEDED);
        assertThat(executionResult.isSuccess()).isTrue();
        assertThat(executionResult.isStopped()).isFalse();
        assertThat(executionResult.isCancelled()).isFalse();
    }

    @Test
    void stopped_shouldNotBeReportedAsSuccess() {
        // Given / When
        ExecutionResult<String> executionResult = ExecutionResult.stopped("partial", null);

        // Then
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.STOPPED);
        assertThat(executionResult.isStopped()).isTrue();
        assertThat(executionResult.isSuccess()).isFalse();
    }

    @Test
    void cancelled_shouldNotBeReportedAsFailureOrSuccess() {
        // Given
        RuntimeException cancellation = new RuntimeException("cancelled");

        // When
        ExecutionResult<Void> executionResult = ExecutionResult.cancelled(null, null, cancellation);

        // Then
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.CANCELLED);
        assertThat(executionResult.isCancelled()).isTrue();
        assertThat(executionResult.isSuccess()).isFalse();
        assertThat(executionResult.isFailed()).isFalse();
        assertThat(executionResult.getError()).isSameAs(cancellation);
    }

    @Test
    void failure_shouldExposeFailedOutcome() {
        // Given
        RuntimeException error = new RuntimeException("boom");

        // When
        ExecutionResult<Void> executionResult = ExecutionResult.failure(error, null);

        // Then
        assertThat(executionResult.getOutcome()).isEqualTo(ExecutionOutcome.FAILED);
        assertThat(executionResult.isFailed()).isTrue();
        assertThat(executionResult.isSuccess()).isFalse();
        assertThat(executionResult.getError()).isSameAs(error);
    }
}
