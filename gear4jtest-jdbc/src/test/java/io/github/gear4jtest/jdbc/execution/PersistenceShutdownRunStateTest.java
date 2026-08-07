package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceShutdownRunStateTest {
    @Test
    void retryState_shouldRetainFirstFailureWhenDeadlineIsReached() {
        // Given
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1, 1);
        PersistenceShutdownRunState state = new PersistenceShutdownRunState(buffer, Duration.ofMillis(1), false);
        Exception jdbcFailure = new IllegalStateException("jdbc");
        Exception deadlineFailure = new IllegalStateException("deadline");
        long beforeFailure = System.nanoTime();

        // When
        state.recordAttempt();
        state.recordRetryableFailure(jdbcFailure, Duration.ofMillis(2));
        state.recordDeadlineFailure(deadlineFailure);

        // Then
        assertThat(state.attempts()).isEqualTo(1);
        assertThat(state.nextAttemptNanos()).isGreaterThanOrEqualTo(beforeFailure);
        assertThat(state.lastFailure()).isSameAs(jdbcFailure);
        assertThat(state.lastFailure().getSuppressed()).containsExactly(deadlineFailure);
        assertThat(state.retryable()).isFalse();
    }

    @Test
    void deadlineArithmetic_shouldSaturateInsteadOfOverflowing() {
        assertThat(PersistenceShutdownDeadline.saturatedNanos(Duration.ofSeconds(Long.MAX_VALUE)))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(PersistenceShutdownDeadline.addSaturated(Long.MAX_VALUE - 1, 2))
                .isEqualTo(Long.MAX_VALUE);
    }
}
