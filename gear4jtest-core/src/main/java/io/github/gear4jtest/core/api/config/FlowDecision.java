package io.github.gear4jtest.core.api.config;

/**
 * Flow-control decision produced from a child station result.
 *
 * <ul>
 * <li>{@link #PROCEED}: continue normally.</li>
 * <li>{@link #INTERRUPT}: interrupt immediately and propagate stop, failure or
 * cancellation.</li>
 * <li>{@link #MARK_AND_PROCEED}: record a collected failure and continue.</li>
 * </ul>
 */
public enum FlowDecision {
    PROCEED, INTERRUPT, MARK_AND_PROCEED
}
