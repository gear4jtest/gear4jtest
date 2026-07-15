package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Monotonic end-to-end deadline shared by all persistence shutdown steps. */
final class PersistenceShutdownDeadline {
    private final Instant startedAt;
    private final long startedNanos;
    private final long deadlineNanos;

    private PersistenceShutdownDeadline(Instant startedAt, long startedNanos, long deadlineNanos) {
        this.startedAt = startedAt;
        this.startedNanos = startedNanos;
        this.deadlineNanos = deadlineNanos;
    }

    static PersistenceShutdownDeadline start(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        long startedNanos = System.nanoTime();
        return new PersistenceShutdownDeadline(Instant.now(), startedNanos, deadlineAfter(startedNanos, timeout));
    }

    Instant startedAt() {
        return startedAt;
    }

    Duration elapsed() {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
    }

    boolean reached() {
        return deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos;
    }

    long remainingNanos() {
        return deadlineNanos == Long.MAX_VALUE ? Long.MAX_VALUE
                : Math.max(0L, deadlineNanos - System.nanoTime());
    }

    private static long deadlineAfter(long startedNanos, Duration timeout) {
        long timeoutNanos = safeToNanos(timeout);
        if (timeoutNanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        try {
            return Math.addExact(startedNanos, timeoutNanos);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeToNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
