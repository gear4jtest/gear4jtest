package io.github.gear4jtest.core.api.config;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;

/**
 * Converts a child {@link StationLogTrace} into the {@link FlowDecision}
 * dictated by a {@link FlowConfig}.
 *
 * <p>
 * This component is stateless, deterministic and easy to test.
 * </p>
 */
public final class FlowDecider {
    private FlowDecider() {
    }

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

        // 3) STOPPED (functional stop)
        if (status == StationLogStatus.STOPPED) {
            return switch (config.stopPolicy()) {
                case PROPAGATE_STOP, TREAT_AS_FAILURE -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
            };
        }

        // 4) CANCELLED (technical cancellation)
        if (status == StationLogStatus.CANCELLED) {
            return switch (config.cancelPolicy()) {
                case PROPAGATE_CANCEL, TREAT_AS_FAILURE -> FlowDecision.INTERRUPT;
                case IGNORE_AND_CONTINUE -> FlowDecision.PROCEED;
            };
        }

        // Safety fallback: unknown child statuses interrupt the current flow.
        return FlowDecision.INTERRUPT;
    }
}
