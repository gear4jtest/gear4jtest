package io.github.gear4jtest.core.engine.runner;

import java.util.List;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.event.OperationErrorEvent;
import io.github.gear4jtest.core.persistence.StationLog;

public class StationErrorPolicyExecutor {

    @SuppressWarnings("unchecked")
    public StationLog apply(
            AbstractStation<?, ?> station,
            Object input,
            StationExecutionContext stationCtx,
            Exception exception) {

        StationLog record = stationCtx.getRecord();

        if (stationCtx.getGlobalContext().getEventManager() != null) {
            stationCtx.getGlobalContext().getEventManager().publish(
                    new OperationErrorEvent(
                            stationCtx.getGlobalContext().getPipelineId(),
                            stationCtx.getGlobalContext().getExecutionId(),
                            station.getId(),
                            input,
                            exception));
        }

        if (record.getStatus() != StationLog.Status.RUNNING) {
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

    private StationLog applyIgnorePolicy(
            AbstractStation station,
            Object input,
            StationExecutionContext stationCtx,
            StationLog record,
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