package io.github.gear4jtest.core.api.config;

/**
 * Policy applied when a child station ends with {@code STOPPED}.
 */
public enum StopPolicy {
    /** Stop the current flow and propagate the stop signal. */
    PROPAGATE_STOP,
    /** Ignore the stop signal and continue with the previous input. */
    IGNORE_AND_CONTINUE,
    /** Treat the stop signal as a failure. */
    TREAT_AS_FAILURE
}
