package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;

/**
 * Mutable retry state retained for one run during a bounded persistence
 * shutdown.
 */
final class PersistenceShutdownRunState {
    private final OperationRecordBuffer buffer;
    private boolean finalizationPending;
    private int attempts;
    private Exception lastFailure;
    private long nextAttemptNanos;
    private long backoffNanos;
    private boolean retryable = true;

    PersistenceShutdownRunState(OperationRecordBuffer buffer,
                                Duration initialBackoff,
                                boolean finalizationPending) {
        this.buffer = buffer;
        this.backoffNanos = PersistenceShutdownDeadline.saturatedNanos(initialBackoff);
        this.finalizationPending = finalizationPending;
    }

    OperationRecordBuffer buffer() {
        return buffer;
    }

    int attempts() {
        return attempts;
    }

    boolean finalizationPending() {
        return finalizationPending;
    }

    Exception lastFailure() {
        return lastFailure;
    }

    long nextAttemptNanos() {
        return nextAttemptNanos;
    }

    boolean retryable() {
        return retryable;
    }

    void recordAttempt() {
        attempts++;
    }

    void recordFinalizationSuccess() {
        finalizationPending = false;
    }

    void recordRetryableFailure(Exception failure, Duration maxBackoff) {
        this.lastFailure = failure;
        this.nextAttemptNanos = PersistenceShutdownDeadline.addSaturated(System.nanoTime(), backoffNanos);
        this.backoffNanos = nextBackoff(backoffNanos, maxBackoff);
    }

    void recordTerminalFailure(Exception failure) {
        this.lastFailure = failure;
        this.retryable = false;
    }

    void recordDeadlineFailure(Exception failure) {
        if (lastFailure == null) {
            lastFailure = failure;
        } else {
            lastFailure.addSuppressed(failure);
        }
        retryable = false;
    }

    private static long nextBackoff(long currentBackoffNanos, Duration maxBackoff) {
        long maxBackoffNanos = PersistenceShutdownDeadline.saturatedNanos(maxBackoff);
        if (currentBackoffNanos >= maxBackoffNanos || currentBackoffNanos > Long.MAX_VALUE / 2L) {
            return maxBackoffNanos;
        }
        return Math.min(maxBackoffNanos, currentBackoffNanos * 2L);
    }
}
