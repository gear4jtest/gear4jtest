package io.github.gear4jtest.core.api.config;

import io.github.gear4jtest.core.persistence.StationLog;

/**
 * Normalise un {@link StationLog} enfant en une {@link FlowDecision} selon une {@link FlowConfig}.
 *
 * <p>Ce composant est stateless, pur et facilement testable.
 */
public final class FlowDecider {

    private FlowDecider() {}

    public static FlowDecision decide(StationLog childLog, FlowConfig config) {
        StationLog.Status status = childLog.getStatus();

        // 1) Happy path
        if (status == StationLog.Status.SUCCEEDED || status == StationLog.Status.SKIPPED) {
            return FlowDecision.PROCEED;
        }

        // 2) Failures
        if (status == StationLog.Status.FAILED) {
            return switch (config.failurePolicy()) {
                case FAIL_FAST -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
                case COLLECT_AND_FAIL -> FlowDecision.MARK_AND_PROCEED;
            };
        }

        // 3) STOPPED (fonctionnel)
        if (status == StationLog.Status.STOPPED) {
            return switch (config.stopPolicy()) {
                case PROPAGATE_STOP, TREAT_AS_FAILURE -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
            };
        }

        // 4) CANCELLED (technique)
        if (status == StationLog.Status.CANCELLED) {
            return switch (config.cancelPolicy()) {
                case PROPAGATE_CANCEL, TREAT_AS_FAILURE -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
            };
        }

        // Sécurité : on n'a pas de sémantique pour ce status -> on interrompt.
        return FlowDecision.INTERRUPT;
    }
}
