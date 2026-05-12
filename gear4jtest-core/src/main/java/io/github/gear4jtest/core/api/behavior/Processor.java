package io.github.gear4jtest.core.api.behavior;

import io.github.gear4jtest.core.api.context.StationExecutionContext;

/**
 * Processor hook executed around a station invocation.
 */
public interface Processor {

    <I> void beforeExecution(I input, StationExecutionContext ctx) throws Exception;

    void afterExecution(Object result, StationExecutionContext context);

    default FailureMode beforeExecutionFailureMode() {
        return FailureMode.CONTINUE;
    }

    default FailureMode afterExecutionFailureMode() {
        return FailureMode.CONTINUE;
    }

    enum FailureMode {
        CONTINUE, FAIL_STATION
    }
}
