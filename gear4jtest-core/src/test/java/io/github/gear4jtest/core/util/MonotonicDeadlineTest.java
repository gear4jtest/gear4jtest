package io.github.gear4jtest.core.util;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonotonicDeadlineTest {
    @Test
    void remainingNanos_shouldPreserveSubMillisecondPrecision() {
        // Given
        AtomicLong nanoTime = new AtomicLong(100L);
        MonotonicDeadline deadline = MonotonicDeadline.start(Duration.ofNanos(500L), nanoTime::get);

        // When / Then
        assertThat(deadline.remainingNanos()).isEqualTo(500L);
        nanoTime.addAndGet(499L);
        assertThat(deadline.remainingNanos()).isOne();
        assertThat(deadline.reached()).isFalse();
        nanoTime.incrementAndGet();
        assertThat(deadline.remainingNanos()).isZero();
        assertThat(deadline.reached()).isTrue();
    }

    @Test
    void toNanosSaturated_shouldSaturateExtremeDurations() {
        assertThat(MonotonicDeadline.toNanosSaturated(Duration.ofSeconds(Long.MAX_VALUE)))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void remainingNanos_shouldRemainValidAcrossNanoTimeWraparound() {
        // Given
        AtomicLong nanoTime = new AtomicLong(Long.MAX_VALUE - 5L);
        MonotonicDeadline deadline = MonotonicDeadline.start(Duration.ofNanos(20L), nanoTime::get);

        // When / Then
        nanoTime.set(Long.MIN_VALUE + 4L);
        assertThat(deadline.remainingNanos()).isEqualTo(10L);
    }

    @Test
    void start_shouldRejectNegativeDuration() {
        assertThatThrownBy(() -> MonotonicDeadline.start(Duration.ofNanos(-1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duration must not be negative");
    }
}
