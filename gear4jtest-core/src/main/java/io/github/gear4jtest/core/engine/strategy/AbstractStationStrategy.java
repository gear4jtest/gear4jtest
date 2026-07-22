package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.SkipDecision;
import io.github.gear4jtest.core.api.behavior.SkipPhase;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.exception.StationExecutionException;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public abstract class AbstractStationStrategy<S extends AbstractStation<?, ?>> implements StationExecutionStrategy<S> {
    /**
     * Captures the input/output type variables of an {@link Operator} once, so the
     * unchecked cast on the input is confined to a single place instead of
     * propagating through every caller that only sees {@code Object}.
     */
    @SuppressWarnings("unchecked")
    private static <I, O> O invokeFallback(Operator<I, O> fallback, Object input, StationExecutionContext ctx) {
        return fallback.transform((I) input, ctx);
    }

    @Override
    public StationLogTrace run(S station, Object input, StationExecutionContext context, StationRunner runner) {
        Object result = null;
        Exception mainException = null;
        try {
            if (context.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                EngineStationContexts.trace(context).markCancelled(context.getGlobalContext().getCancellationToken()
                        .cancellationCause().orElse(null));
                return EngineStationContexts.trace(context);
            }

            setUp(station, input, context);

            SkipDecision preCause = runSkippers(station, input, context, SkipPhase.PRE_PROCESSORS);
            if (preCause.shouldSkip()) {
                result = handleSkip(station, input, context, EngineStationContexts.trace(context), preCause.reason());
                return EngineStationContexts.trace(context);
            }

            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : station.getProcessors()) {
                    try {
                        processor.beforeExecution(input, context);
                    } catch (Exception e) {
                        EngineStationContexts.trace(context).addErrorHandlerException(e);
                        if (processor.beforeExecutionFailureMode() == Processor.FailureMode.FAIL_STATION) {
                            throw e;
                        }
                    }
                }
            }

            afterBeforeProcessors(station, input, context);

            SkipDecision postCause = runSkippers(station, input, context, SkipPhase.POST_PROCESSORS);
            if (postCause.shouldSkip()) {
                result = handleSkip(station, input, context, EngineStationContexts.trace(context), postCause.reason());
                return EngineStationContexts.trace(context);
            }

            result = doExecute(station, input, runner, context);

            if (EngineStationContexts.trace(context).getStatus() == StationLogStatus.RUNNING) {
                EngineStationContexts.trace(context).markSuccess(result);
            } else {
                EngineStationContexts.trace(context).setOutput(result);
                if (EngineStationContexts.trace(context).getEndedAt() == null) {
                    EngineStationContexts.trace(context).setEndedAt(java.time.Instant.now());
                }
            }

            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : station.getProcessors()) {
                    try {
                        processor.afterExecution(result, context);
                    } catch (Exception e) {
                        EngineStationContexts.trace(context).addErrorHandlerException(e);
                        if (processor.afterExecutionFailureMode() == Processor.FailureMode.FAIL_STATION) {
                            throw e;
                        }
                    }
                }
            }

            afterProcessors(station, result, context);

            return EngineStationContexts.trace(context);
        } catch (Exception e) {
            mainException = e;
            throw StationExecutionException.wrap(e);
        } finally {
            try {
                List<Throwable> errorsForRelease = buildErrorListForRelease(EngineStationContexts.trace(context),
                                                                            mainException);
                release(station, result, context, errorsForRelease);
            } catch (Exception releaseException) {
                EngineStationContexts.trace(context).addErrorHandlerException(releaseException);
            }
        }
    }

    protected SkipDecision runSkippers(S station, Object input, StationExecutionContext ctx, SkipPhase phase) {
        List<StationSkipper> skippers = station.getSkippers();
        if (skippers == null || skippers.isEmpty()) {
            return SkipDecision.dontSkip();
        }

        for (StationSkipper skipper : skippers) {
            if (skipper == null || skipper.phase() != phase) {
                continue;
            }
            SkipDecision skipDecision = skipper.shouldSkip(input, ctx);
            if (skipDecision.shouldSkip()) {
                return skipDecision;
            }
        }
        return SkipDecision.dontSkip();
    }

    protected Object handleSkip(S station,
                                Object input,
                                StationExecutionContext ctx,
                                StationLogTrace stationLog,
                                String reason) {

        if (station.getFallbackOperator() != null) {
            try {
                Object res = invokeFallback(station.getFallbackOperator(), input, ctx);
                markSkipped(stationLog, reason);
                stationLog.setOutput(res);
                return res;
            } catch (Exception e) {
                stationLog.markFailed(e);
                return null;
            }
        }

        if (station.isUnary()) {
            markSkipped(stationLog, reason);
            stationLog.setOutput(input);
            return input;
        }

        markSkipped(stationLog, reason);
        return null;
    }

    private void markSkipped(StationLogTrace stationLog, String reason) {
        if (reason != null) {
            stationLog.markSkipped(reason);
        } else {
            stationLog.markSkipped();
        }
    }

    protected List<Throwable> buildErrorListForRelease(StationLogTrace stationLog, Exception mainException) {
        List<Throwable> throwables = stationLog.getThrowables();
        if (throwables == null || throwables.isEmpty()) {
            if (mainException == null) {
                return List.of();
            }
            List<Throwable> result = new ArrayList<>();
            result.add(mainException);
            return result;
        }

        if (mainException != null && !throwables.contains(mainException)) {
            List<Throwable> result = new ArrayList<>(throwables);
            result.add(mainException);
            return result;
        }
        return throwables;
    }

    protected void setUp(S station, Object input, StationExecutionContext operationExecution) {
    }

    protected void afterBeforeProcessors(S station, Object input, StationExecutionContext operationExecution) {
    }

    protected void afterProcessors(S station, Object result, StationExecutionContext operationExecution) {
    }

    protected void release(S station, Object result, StationExecutionContext context, List<Throwable> errors) {
    }

    protected <T> T clonePayload(T payload, StationExecutionContext context) {
        return EngineStationContexts.support(context).getPayloadCloner().clonePayload(payload);
    }

    protected abstract Object doExecute(S station,
                                        Object input,
                                        StationRunner runner,
                                        StationExecutionContext opContext)
            throws Exception;
}
