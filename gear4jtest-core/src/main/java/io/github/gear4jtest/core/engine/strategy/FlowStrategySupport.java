package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.persistence.StationLog;

final class FlowStrategySupport {

    private FlowStrategySupport() {
    }

    static FlowConfig resolveFlowConfig(FlowConfig config) {
        return config != null ? config : FlowConfig.DEFAULT;
    }

    static Throwable representativeThrowable(StationLog childLog, String fallbackMessage) {
        if (childLog.getThrowables() != null && !childLog.getThrowables().isEmpty()) {
            return childLog.getThrowables().get(0);
        }

        if (childLog.getErrorMessage() != null && !childLog.getErrorMessage().isBlank()) {
            return new RuntimeException(childLog.getErrorMessage());
        }

        return new RuntimeException(fallbackMessage);
    }

    static Exception representativeException(StationLog childLog, String fallbackMessage) {
        Throwable throwable = representativeThrowable(childLog, fallbackMessage);
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable.getMessage(), throwable);
    }

    static void applyInterruptToParentLog(StationLog parent, StationLog child, FlowConfig config) {
        StationLog.Status childStatus = child.getStatus();

        Exception representative = representativeException(
                child,
                "Interrupted child without error details: " + child.getOperationId());

        if (childStatus == StationLog.Status.FAILED) {
            parent.markFailed(representative);
            return;
        }

        if (childStatus == StationLog.Status.STOPPED) {
            if (config.stopPolicy() == StopPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markStopped(representative);
            }
            return;
        }

        if (childStatus == StationLog.Status.CANCELLED) {
            if (config.cancelPolicy() == CancelPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markCancelled(representative);
            }
            return;
        }

        parent.markFailed(representative);
    }
}
