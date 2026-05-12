package io.github.gear4jtest.core.api.behavior;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

/**
 * Declarative skip rule attached to a station.
 *
 * <p>
 * Pre-processor skippers are evaluated before station preparation.
 * Post-processor skippers are evaluated after preparation, when station-scoped
 * context such as resolved parameters is available.
 * </p>
 */
public final class StationSkipper {

    private final SkipPhase phase;
    private final StationSkipTest test;
    private final String reason;

    private StationSkipper(SkipPhase phase, StationSkipTest test, String reason) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.test = Objects.requireNonNull(test, "test");
        this.reason = reason;
    }

    /**
     * Creates a pre-processor skipper without a reason.
     */
    public static StationSkipper pre(StationSkipTest predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new StationSkipper(SkipPhase.PRE_PROCESSORS, predicate, null);
    }

    /**
     * Creates a pre-processor skipper with an optional diagnostic reason.
     */
    public static StationSkipper pre(StationSkipTest predicate, String reason) {
        Objects.requireNonNull(predicate, "predicate");
        return new StationSkipper(SkipPhase.PRE_PROCESSORS, predicate, reason);
    }

    /**
     * Creates a post-processor skipper without a reason.
     */
    public static StationSkipper post(StationSkipTest predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new StationSkipper(SkipPhase.POST_PROCESSORS, predicate, null);
    }

    // ------------------------------------------------------------------------
    // Factories DSL
    // ------------------------------------------------------------------------

    /**
     * Creates a post-processor skipper with an optional diagnostic reason.
     */
    public static StationSkipper post(StationSkipTest predicate, String reason) {
        Objects.requireNonNull(predicate, "predicate");
        return new StationSkipper(SkipPhase.POST_PROCESSORS, predicate, reason);
    }

    public SkipPhase phase() {
        return phase;
    }

    public SkipDecision shouldSkip(Object input, StationExecutionContext stationCtx) {
        var skipped = test.test(input, stationCtx);
        return skipped ? SkipDecision.skip(reason) : SkipDecision.dontSkip();
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }
}
