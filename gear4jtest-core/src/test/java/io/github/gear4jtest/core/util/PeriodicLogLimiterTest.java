package io.github.gear4jtest.core.util;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PeriodicLogLimiterTest {
    @Test
    void acquire_shouldPermitFirstSignalThenReportSuppressedSignalsAfterInterval() {
        // Given
        AtomicLong nanoTime = new AtomicLong(10L);
        PeriodicLogLimiter limiter = PeriodicLogLimiter.every(Duration.ofNanos(100L), nanoTime::get);

        // When
        PeriodicLogLimiter.Emission first = limiter.acquire();
        PeriodicLogLimiter.Emission suppressedOne = limiter.acquire();
        PeriodicLogLimiter.Emission suppressedTwo = limiter.acquire();
        nanoTime.addAndGet(100L);
        PeriodicLogLimiter.Emission reminder = limiter.acquire();

        // Then
        assertThat(first.permitted()).isTrue();
        assertThat(first.suppressedSincePreviousEmission()).isZero();
        assertThat(suppressedOne.permitted()).isFalse();
        assertThat(suppressedTwo.permitted()).isFalse();
        assertThat(reminder.permitted()).isTrue();
        assertThat(reminder.suppressedSincePreviousEmission()).isEqualTo(2L);
    }

    @Test
    void acquire_shouldUseMonotonicDeadlineAcrossNanoTimeOverflow() {
        // Given
        AtomicLong nanoTime = new AtomicLong(Long.MAX_VALUE - 5L);
        PeriodicLogLimiter limiter = PeriodicLogLimiter.every(Duration.ofNanos(10L), nanoTime::get);

        // When / Then
        assertThat(limiter.acquire().permitted()).isTrue();
        nanoTime.set(Long.MIN_VALUE + 3L);
        assertThat(limiter.acquire().permitted()).isFalse();
        nanoTime.set(Long.MIN_VALUE + 4L);
        assertThat(limiter.acquire().permitted()).isTrue();
    }

    @Test
    void every_shouldRejectNonPositiveIntervals() {
        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PeriodicLogLimiter.every(Duration.ZERO))
                .withMessage("interval must be greater than zero");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PeriodicLogLimiter.every(Duration.ofNanos(-1L)))
                .withMessage("interval must be greater than zero");
    }
}
