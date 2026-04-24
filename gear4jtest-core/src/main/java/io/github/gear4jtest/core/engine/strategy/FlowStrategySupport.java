package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.model.StationLogStatus;

import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;

final class FlowStrategySupport {

    private FlowStrategySupport() {
    }

    static FlowConfig resolveFlowConfig(FlowConfig config) {
        return config != null ? config : FlowConfig.DEFAULT;
    }

    static Throwable representativeThrowable(StationLogTrace childLog, String fallbackMessage) {
        if (childLog.getThrowables() != null && !childLog.getThrowables().isEmpty()) {
            return childLog.getThrowables().get(0);
        }

        if (childLog.getErrorMessage() != null && !childLog.getErrorMessage().isBlank()) {
            return new RuntimeException(childLog.getErrorMessage());
        }

        return new RuntimeException(fallbackMessage);
    }

    static Exception representativeException(StationLogTrace childLog, String fallbackMessage) {
        Throwable throwable = representativeThrowable(childLog, fallbackMessage);
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable.getMessage(), throwable);
    }

    static void applyInterruptToParentLog(StationLogTrace parent, StationLogTrace child, FlowConfig config) {
        StationLogStatus childStatus = child.getStatus();

        Exception representative = representativeException(
                child,
                "Interrupted child without error details: " + child.getOperationId());

        if (childStatus == StationLogStatus.FAILED) {
            parent.markFailed(representative);
            return;
        }

        if (childStatus == StationLogStatus.STOPPED) {
            if (config.stopPolicy() == StopPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markStopped(representative);
            }
            return;
        }

        if (childStatus == StationLogStatus.CANCELLED) {
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
