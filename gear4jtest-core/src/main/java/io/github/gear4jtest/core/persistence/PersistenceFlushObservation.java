package io.github.gear4jtest.core.persistence;

import java.time.Duration;
import java.util.Objects;

/**
 * A completed persistence-flush attempt exposed without coupling observers to a
 * storage-provider implementation.
 *
 * @param duration monotonic elapsed time from admission to terminal outcome
 * @param trigger  framework-owned source of the flush attempt
 * @param outcome  terminal outcome of the attempt
 */
public record PersistenceFlushObservation(Duration duration, Trigger trigger, Outcome outcome) {
    public PersistenceFlushObservation {
        Objects.requireNonNull(duration, "duration must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    /** Closed, low-cardinality set of flush admission paths. */
    public enum Trigger {
        ASYNC,
        EXPLICIT,
        TERMINAL,
        SHUTDOWN
    }

    /** Closed, low-cardinality set of terminal flush outcomes. */
    public enum Outcome {
        SUCCEEDED,
        FAILED,
        REJECTED,
        TIMED_OUT,
        INTERRUPTED
    }
}
