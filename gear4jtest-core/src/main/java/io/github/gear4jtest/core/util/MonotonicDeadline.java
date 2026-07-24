package io.github.gear4jtest.core.util;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Monotonic deadline used to share one timeout budget across several blocking
 * steps.
 */
public final class MonotonicDeadline {
    private final LongSupplier nanoTime;
    private final long startedNanos;
    private final long timeoutNanos;

    private MonotonicDeadline(LongSupplier nanoTime, long startedNanos, long timeoutNanos) {
        this.nanoTime = nanoTime;
        this.startedNanos = startedNanos;
        this.timeoutNanos = timeoutNanos;
    }

    /**
     * Starts a deadline backed by {@link System#nanoTime()}.
     *
     * @param timeout non-negative timeout
     * @return the new deadline
     */
    public static MonotonicDeadline start(Duration timeout) {
        return start(timeout, System::nanoTime);
    }

    static MonotonicDeadline start(Duration timeout, LongSupplier nanoTime) {
        Objects.requireNonNull(nanoTime, "nanoTime must not be null");
        long timeoutNanos = toNanosSaturated(timeout);
        return new MonotonicDeadline(nanoTime, nanoTime.getAsLong(), timeoutNanos);
    }

    /**
     * Converts a non-negative duration to nanoseconds, saturating instead of
     * overflowing.
     *
     * @param duration duration to convert
     * @return nanoseconds, or {@link Long#MAX_VALUE} when the exact value does not
     *         fit
     */
    public static long toNanosSaturated(Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Returns the remaining timeout budget in nanoseconds.
     *
     * @return zero once the deadline has been reached
     */
    public long remainingNanos() {
        long elapsedNanos = nanoTime.getAsLong() - startedNanos;
        if (elapsedNanos <= 0L) {
            return timeoutNanos;
        }
        return elapsedNanos >= timeoutNanos ? 0L : timeoutNanos - elapsedNanos;
    }

    /**
     * Returns whether the timeout budget has been exhausted.
     */
    public boolean reached() {
        return remainingNanos() == 0L;
    }
}
