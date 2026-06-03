package io.github.gear4jtest.core.engine.runner;

import java.util.Objects;

import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.exception.StationExecutionException;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StationExceptionBoundaryRunner implements StationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StationExceptionBoundaryRunner.class);
    private final StationRunner delegate;
    private final StationErrorPolicyExecutor errorPolicyExecutor;

    public StationExceptionBoundaryRunner(StationRunner delegate, StationErrorPolicyExecutor errorPolicyExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.errorPolicyExecutor = Objects.requireNonNull(errorPolicyExecutor, "errorPolicyExecutor must not be null");
    }

    @Override
    public StationLogTrace run(Object input, AbstractStation<?, ?> station, StationExecutionContext ctx) {
        try {
            return delegate.run(input, station, ctx);
        } catch (Error error) {
            throw error;
        } catch (Exception throwable) {
            Exception effectiveException = StationExecutionException.unwrap(throwable);
            try {
                return errorPolicyExecutor.apply(station, input, ctx, effectiveException);
            } catch (Error error) {
                throw error;
            } catch (Exception policyFailure) {
                LOGGER.error("Station error policy failed. Falling back to markFailed. stationId={}", station.getId(),
                             policyFailure);

                StationLogTrace record = ctx.getRecord();
                if (record.getStatus() == StationLogStatus.RUNNING) {
                    record.markFailed(effectiveException);
                } else {
                    record.addErrorHandlerException(effectiveException);
                }
                record.addErrorHandlerException(policyFailure);
                return record;
            }
        }
    }
}
