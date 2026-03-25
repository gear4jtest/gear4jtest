package io.github.gear4jtest.core.api.behavior;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

/**
 * Skipper déclaratif attaché à une station.
 *
 * <p>PRE : simple (souvent input + global context).
 * <p>POST : dépend de la préparation (processors.beforeExecution).
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
     * Skipper PRE basé uniquement sur (input, globalCtx).
     */
    public static StationSkipper pre(StationSkipTest predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new StationSkipper(
                SkipPhase.PRE_PROCESSORS,
                predicate,
                null);
    }

    /**
     * Skipper PRE basé sur (input, globalCtx), avec reason.
     */
    public static StationSkipper pre(StationSkipTest predicate, String reason) {
        Objects.requireNonNull(predicate, "predicate");
        return new StationSkipper(
                SkipPhase.PRE_PROCESSORS,
                predicate,
                reason);
    }

    /**
     * Skipper POST basé sur (input, globalCtx, stationCtx).
     */
    public static StationSkipper post(StationSkipTest predicate) {
        Objects.requireNonNull(predicate, "predicate");
        return new StationSkipper(SkipPhase.POST_PROCESSORS, predicate, null);
    }

    // ------------------------------------------------------------------------
    // Factories DSL
    // ------------------------------------------------------------------------

    /**
     * Skipper POST avec reason.
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
