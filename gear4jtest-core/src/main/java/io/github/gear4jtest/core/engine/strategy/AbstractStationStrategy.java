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
                context.getRecord().markCancelled(context.getGlobalContext().getCancellationToken()
                        .cancellationCause().orElse(null));
                return context.getRecord();
            }

            setUp(station, input, context);

            SkipDecision preCause = runSkippers(station, input, context, SkipPhase.PRE_PROCESSORS);
            if (preCause.shouldSkip()) {
                result = handleSkip(station, input, context, context.getRecord(), preCause.reason());
                return context.getRecord();
            }

            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : station.getProcessors()) {
                    try {
                        processor.beforeExecution(input, context);
                    } catch (Exception e) {
                        context.getRecord().addErrorHandlerException(e);
                        if (processor.beforeExecutionFailureMode() == Processor.FailureMode.FAIL_STATION) {
                            throw e;
                        }
                    }
                }
            }

            SkipDecision postCause = runSkippers(station, input, context, SkipPhase.POST_PROCESSORS);
            if (postCause.shouldSkip()) {
                result = handleSkip(station, input, context, context.getRecord(), postCause.reason());
                return context.getRecord();
            }

            result = doExecute(station, input, runner, context);

            if (context.getRecord().getStatus() == StationLogStatus.RUNNING) {
                context.getRecord().markSuccess(result);
            } else {
                context.getRecord().setOutput(result);
                if (context.getRecord().getEndedAt() == null) {
                    context.getRecord().setEndedAt(java.time.Instant.now());
                }
            }

            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : station.getProcessors()) {
                    try {
                        processor.afterExecution(result, context);
                    } catch (Exception e) {
                        context.getRecord().addErrorHandlerException(e);
                        if (processor.afterExecutionFailureMode() == Processor.FailureMode.FAIL_STATION) {
                            throw e;
                        }
                    }
                }
            }

            return context.getRecord();
        } catch (Exception e) {
            mainException = e;
            throw StationExecutionException.wrap(e);
        } finally {
            try {
                List<Throwable> errorsForRelease = buildErrorListForRelease(context.getRecord(), mainException);
                release(station, result, context, errorsForRelease);
            } catch (Exception releaseException) {
                context.getRecord().addErrorHandlerException(releaseException);
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
                                StationLogTrace record,
                                String reason) {

        if (station.getFallbackOperator() != null) {
            try {
                Object res = invokeFallback(station.getFallbackOperator(), input, ctx);
                markSkipped(record, reason);
                record.setOutput(res);
                return res;
            } catch (Exception e) {
                record.addErrorHandlerException(e);
                record.markSkipped(e);
                return null;
            }
        }

        if (Boolean.TRUE.equals(station.getUnary())) {
            markSkipped(record, reason);
            record.setOutput(input);
            return input;
        }

        markSkipped(record, reason);
        return null;
    }

    private void markSkipped(StationLogTrace record, String reason) {
        if (reason != null) {
            record.markSkipped(reason);
        } else {
            record.markSkipped();
        }
    }

    protected List<Throwable> buildErrorListForRelease(StationLogTrace record, Exception mainException) {
        List<Throwable> throwables = record.getThrowables();
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

    protected void release(S station, Object result, StationExecutionContext context, List<Throwable> errors) {
    }

    protected <T> T clonePayload(T payload, StationExecutionContext context) {
        return context.getSupport().getPayloadCloner().clonePayload(payload);
    }

    protected abstract Object doExecute(S station,
                                        Object input,
                                        StationRunner runner,
                                        StationExecutionContext opContext)
            throws Exception;
}
