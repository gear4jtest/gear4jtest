package io.github.gear4jtest.core.spi.extension;

import java.util.concurrent.ExecutorService;

import io.github.gear4jtest.core.api.context.ExecutionContext;

/**
 * Extension point used to decorate executors created or used by the runtime.
 *
 * <p>
 * Typical use cases are MDC propagation, tracing context propagation or metrics
 * around asynchronous tasks. A wrapper must not shut down an executor it does
 * not own.
 * </p>
 */
public interface ExecutorWrapperExtension extends RuntimeExtension {
    /**
     * Returns an executor view used by the runtime for the current run.
     *
     * @param delegate original executor
     * @param ctx      execution context of the current run
     * @return decorated executor
     */
    ExecutorService wrapExecutor(ExecutorService delegate, ExecutionContext ctx);
}
