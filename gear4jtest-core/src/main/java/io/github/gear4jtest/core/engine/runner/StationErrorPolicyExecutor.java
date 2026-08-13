package io.github.gear4jtest.core.engine.runner;

import java.util.List;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Condition;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;

public class StationErrorPolicyExecutor {
    /**
     * Captures the input/output type variables of an {@link Operator} once, so the
     * unchecked cast on the input is confined to a single place.
     */
    @SuppressWarnings("unchecked")
    private static <I, O> O invokeFallback(Operator<I, O> fallback, Object input, StationExecutionContext ctx) {
        return fallback.transform((I) input, ctx);
    }

    public StationLogTrace apply(AbstractStation<?, ?> station,
                                 Object input,
                                 StationExecutionContext stationCtx,
                                 Exception exception) {

        StationLogTrace stationLog = EngineStationContexts.trace(stationCtx);

        if (stationLog.getStatus() != StationLogStatus.RUNNING) {
            stationLog.addErrorHandlerException(exception);
            return stationLog;
        }

        List<? extends BaseError<?>> onErrors = station.getOnErrors();
        if (onErrors == null || onErrors.isEmpty()) {
            stationLog.markFailed(exception);
            return stationLog;
        }

        BaseError<?> matched = null;
        for (BaseError<?> error : onErrors) {
            if (error == null) {
                continue;
            }

            Class<? extends Throwable> throwableType = error.getThrowableType();
            if (throwableType != null && !throwableType.isAssignableFrom(exception.getClass())) {
                continue;
            }

            if (!conditionMatches(error, input, stationCtx)) {
                continue;
            }

            matched = error;
            break;
        }

        if (matched == null) {
            stationLog.markFailed(exception);
            return stationLog;
        }

        try {
            if (matched.getAction() != null) {
                matched.getAction().run();
            }
        } catch (Exception handlerException) {
            stationLog.addErrorHandlerException(handlerException);
        }

        SignalType signalType = matched.getSignalType() != null ? matched.getSignalType() : SignalType.FATAL;

        return switch (signalType) {
            case IGNORE -> applyIgnorePolicy(station, input, stationCtx, stationLog, exception);
            case STOP -> {
                stationLog.markStopped(exception);
                yield stationLog;
            }
            default -> {
                stationLog.markFailed(exception);
                yield stationLog;
            }
        };
    }

    private static boolean conditionMatches(BaseError<?> error,
                                            Object input,
                                            StationExecutionContext stationCtx) {
        return conditionMatchesTyped(error, input, stationCtx);
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean conditionMatchesTyped(BaseError<T> error,
                                                     Object input,
                                                     StationExecutionContext stationCtx) {
        Condition<T> condition = error.getCondition();
        return condition == null || condition.test((T) input, stationCtx.getGlobalContext());
    }

    private StationLogTrace applyIgnorePolicy(AbstractStation<?, ?> station,
                                              Object input,
                                              StationExecutionContext stationCtx,
                                              StationLogTrace stationLog,
                                              Exception originalException) {

        if (station.getFallbackOperator() != null) {
            try {
                Object result = invokeFallback(station.getFallbackOperator(), input, stationCtx);
                stationLog.markSuccess(result);
                return stationLog;
            } catch (Exception fallbackException) {
                stationLog.addErrorHandlerException(originalException);
                stationLog.markFailed(fallbackException);
                return stationLog;
            }
        }

        if (station.isUnary()) {
            stationLog.markSuccess(input);
            return stationLog;
        }

        stationLog.markSkipped(originalException);
        return stationLog;
    }
}
