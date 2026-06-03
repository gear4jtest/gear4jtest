package io.github.gear4jtest.core.api.config;

/**
 * Policy applied when a child station ends with {@code CANCELLED}.
 */
public enum CancelPolicy {
    /** Stop the current flow and propagate cancellation. */
    PROPAGATE_CANCEL,
    /** Ignore cancellation and continue with the previous input. */
    IGNORE_AND_CONTINUE,
    /** Treat cancellation as a failure. */
    TREAT_AS_FAILURE
}
