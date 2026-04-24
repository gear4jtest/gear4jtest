package io.github.gear4jtest.core.api.config;

import io.github.gear4jtest.core.model.StationLogStatus;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;

/**
 * Normalise un {@link StationLogTrace} enfant en une {@link FlowDecision} selon une {@link FlowConfig}.
 *
 * <p>Ce composant est stateless, pur et facilement testable.
 */
public final class FlowDecider {

    private FlowDecider() {}

    public static FlowDecision decide(StationLogTrace childLog, FlowConfig config) {
        StationLogStatus status = childLog.getStatus();

        // 1) Happy path
        if (status == StationLogStatus.SUCCEEDED || status == StationLogStatus.SKIPPED) {
            return FlowDecision.PROCEED;
        }

        // 2) Failures
        if (status == StationLogStatus.FAILED) {
            return switch (config.failurePolicy()) {
                case FAIL_FAST -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
                case COLLECT_AND_FAIL -> FlowDecision.MARK_AND_PROCEED;
            };
        }

        // 3) STOPPED (fonctionnel)
        if (status == StationLogStatus.STOPPED) {
            return switch (config.stopPolicy()) {
                case PROPAGATE_STOP, TREAT_AS_FAILURE -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
            };
        }

        // 4) CANCELLED (technique)
        if (status == StationLogStatus.CANCELLED) {
            return switch (config.cancelPolicy()) {
                case PROPAGATE_CANCEL, TREAT_AS_FAILURE -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
            };
        }

        // Sécurité : on n'a pas de sémantique pour ce status -> on interrompt.
        return FlowDecision.INTERRUPT;
    }
}
