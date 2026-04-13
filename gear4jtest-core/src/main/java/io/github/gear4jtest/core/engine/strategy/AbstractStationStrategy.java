package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.SkipDecision;
import io.github.gear4jtest.core.api.behavior.SkipPhase;
import io.github.gear4jtest.core.api.behavior.StationSkipper;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.exception.StationExecutionException;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractStationStrategy<S extends AbstractStation> implements StationExecutionStrategy<S> {

    @SuppressWarnings("unchecked")
    @Override
    public StationLog run(S station, Object input, StationExecutionContext context, StationRunner runner) {
        Object result = null;
        Exception mainException = null;
        try {
            setUp(station, input, context);

            SkipDecision preCause = runSkippers(station, input, context, SkipPhase.PRE_PROCESSORS);
            if (preCause.shouldSkip()) {
                result = handleSkip(station, input, context, context.getRecord(), preCause.reason());
                return context.getRecord();
            }

            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : (List<Processor>) station.getProcessors()) {
                    try {
                        processor.beforeExecution(input, context);
                    } catch (Exception e) {
                        context.getRecord().addErrorHandlerException(e);
                    }
                }
            }

            SkipDecision postCause = runSkippers(station, input, context, SkipPhase.POST_PROCESSORS);
            if (postCause.shouldSkip()) {
                result = handleSkip(station, input, context, context.getRecord(), postCause.reason());
                return context.getRecord();
            }

            result = doExecute(station, input, runner, context);

            if (context.getRecord().getStatus() == StationLog.Status.RUNNING) {
                context.getRecord().markSuccess(result);
            } else {
                context.getRecord().setOutput(result);
                if (context.getRecord().getEndedAt() == null) {
                    context.getRecord().setEndedAt(java.time.Instant.now());
                }
            }

            if (station.getProcessors() != null && !station.getProcessors().isEmpty()) {
                for (Processor processor : (List<Processor>) station.getProcessors()) {
                    try {
                        processor.afterExecution(input, context);
                    } catch (Exception e) {
                        context.getRecord().addErrorHandlerException(e);
                    }
                }
            }

            return context.getRecord();
        } catch (Exception e) {
            mainException = e;
            throw StationExecutionException.wrap(e);
        } finally {
            context.getGlobalContext().popParentOperationId();

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

    protected Object handleSkip(
            S station,
            Object input,
            StationExecutionContext ctx,
            StationLog record,
            String reason) {

        if (station.getFallbackOperator() != null) {
            try {
                Object res = station.getFallbackOperator().transform(input, ctx);
                record.markSuccess(res);
                return res;
            } catch (Exception e) {
                record.addErrorHandlerException(e);
                record.markSkipped(e);
                return null;
            }
        }

        if (Boolean.TRUE.equals(station.getUnary())) {
            record.markSuccess(input);
            return input;
        }

        if (reason != null) {
            record.markSkipped(reason);
        } else {
            record.markSkipped();
        }
        return null;
    }

    protected List<Throwable> buildErrorListForRelease(StationLog record, Exception mainException) {
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

    protected abstract Object doExecute(
            S station,
            Object input,
            StationRunner runner,
            StationExecutionContext opContext) throws Exception;
}
