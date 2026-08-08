package io.github.gear4jtest.external.api;

/**
 * Cumulative invocation, failure and duration counters for one loading phase.
 */
public record GeneratedLoadingPhaseStats(long attempts,
                                         long failures,
                                         long totalDurationNanos,
                                         long maxDurationNanos) {
    static final GeneratedLoadingPhaseStats EMPTY = new GeneratedLoadingPhaseStats(0L, 0L, 0L, 0L);
}
