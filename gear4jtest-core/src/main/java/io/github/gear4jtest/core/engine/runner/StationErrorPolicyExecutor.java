package io.github.gear4jtest.core.engine.runner;

import io.github.gear4jtest.core.model.StationLogStatus;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import java.util.List;

public class StationErrorPolicyExecutor {

    @SuppressWarnings("unchecked")
    public StationLogTrace apply(
            AbstractStation<?, ?> station,
            Object input,
            StationExecutionContext stationCtx,
            Exception exception) {

        StationLogTrace record = stationCtx.getRecord();

        if (record.getStatus() != StationLogStatus.RUNNING) {
            record.addErrorHandlerException(exception);
            return record;
        }

        List<BaseError<Object>> onErrors = (List<BaseError<Object>>) (List<?>) station.getOnErrors();
        if (onErrors == null || onErrors.isEmpty()) {
            record.markFailed(exception);
            return record;
        }

        BaseError<Object> matched = null;
        for (BaseError<Object> error : onErrors) {
            if (error == null) {
                continue;
            }

            Class<? extends Throwable> throwableType = error.getThrowableType();
            if (throwableType != null && !throwableType.isAssignableFrom(exception.getClass())) {
                continue;
            }

            Condition<Object> condition = error.getCondition();
            if (condition != null && !condition.test(input, stationCtx.getGlobalContext())) {
                continue;
            }

            matched = error;
            break;
        }

        if (matched == null) {
            record.markFailed(exception);
            return record;
        }

        try {
            if (matched.getAction() != null) {
                matched.getAction().run();
            }
        } catch (Exception handlerException) {
            record.addErrorHandlerException(handlerException);
        }

        SignalType signalType = matched.getSignalType() != null
                ? matched.getSignalType()
                : SignalType.FATAL;

        return switch (signalType) {
            case IGNORE -> applyIgnorePolicy(station, input, stationCtx, record, exception);
            case STOP -> {
                record.markStopped(exception);
                yield record;
            }
            default -> {
                record.markFailed(exception);
                yield record;
            }
        };
    }

    private StationLogTrace applyIgnorePolicy(
            AbstractStation station,
            Object input,
            StationExecutionContext stationCtx,
            StationLogTrace record,
            Exception originalException) {

        if (station.getFallbackOperator() != null) {
            try {
                Object result = station.getFallbackOperator().transform(input, stationCtx);
                record.markSuccess(result);
                return record;
            } catch (Exception fallbackException) {
                record.addErrorHandlerException(fallbackException);
                record.markSkipped(originalException);
                return record;
            }
        }

        if (Boolean.TRUE.equals(station.getUnary())) {
            record.markSuccess(input);
            return record;
        }

        record.markSkipped(originalException);
        return record;
    }
}
