package io.github.gear4jtest.core.event.durable;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxDispatchPolicyTest {
    @Test
    void defaults_shouldRetryUntilDefaultMaxAttemptsWithExponentialBackoff() {
        // Given
        OutboxDispatchPolicy policy = OutboxDispatchPolicy.defaults();
        RuntimeException failure = new RuntimeException("temporary");

        // Then
        assertThat(policy.maxAttempts()).isEqualTo(5);
        assertThat(policy.shouldRetry(failure, 0)).isTrue();
        assertThat(policy.shouldRetry(failure, 4)).isTrue();
        assertThat(policy.shouldRetry(failure, 5)).isFalse();
        assertThat(policy.retryDelay(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.retryDelay(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(policy.retryDelay(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(policy.retryDelay(10)).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void shouldRetry_shouldHonorCustomPredicateAndValidateFailure() {
        // Given
        OutboxDispatchPolicy policy = OutboxDispatchPolicy.builder()
                .maxAttempts(3)
                .retryableFailurePredicate(IllegalStateException.class::isInstance)
                .build();

        // Then
        assertThat(policy.shouldRetry(new IllegalStateException("retryable"), 1)).isTrue();
        assertThat(policy.shouldRetry(new IllegalArgumentException("terminal"), 1)).isFalse();
        assertThatThrownBy(() -> policy.shouldRetry(null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("failure must not be null");
    }

    @Test
    void retryDelay_shouldCapAtConfiguredMaxBackoff() {
        // Given
        OutboxDispatchPolicy policy = OutboxDispatchPolicy.builder()
                .initialBackoff(Duration.ofMillis(10))
                .maxBackoff(Duration.ofMillis(25))
                .build();

        // Then
        assertThat(policy.retryDelay(1)).isEqualTo(Duration.ofMillis(10));
        assertThat(policy.retryDelay(2)).isEqualTo(Duration.ofMillis(20));
        assertThat(policy.retryDelay(3)).isEqualTo(Duration.ofMillis(25));
    }

    @Test
    void builder_shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> OutboxDispatchPolicy.builder().maxAttempts(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxAttempts must be > 0");
        assertThatThrownBy(() -> OutboxDispatchPolicy.builder().initialBackoff(Duration.ZERO).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("initialBackoff must be > 0");
        assertThatThrownBy(() -> OutboxDispatchPolicy.builder().maxBackoff(Duration.ofMillis(-1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxBackoff must be > 0");
        assertThatThrownBy(() -> OutboxDispatchPolicy.builder().initialBackoff(Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(1)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxBackoff must be >= initialBackoff");
        assertThatThrownBy(() -> OutboxDispatchPolicy.builder().retryableFailurePredicate(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("retryableFailurePredicate must not be null");
        assertThatThrownBy(() -> OutboxDispatchPolicy.builder().initialBackoff(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("initialBackoff must not be null");
        assertThatCode(() -> OutboxDispatchPolicy.builder().maxAttempts(1).build()).doesNotThrowAnyException();
    }
}
