package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;

/**
 * Around-run extension point.
 *
 * <p>
 * Implementations wrap the whole assembly line run. They can add cross-cutting
 * behavior such as timing, tracing, request decoration or policy checks, but
 * should normally delegate exactly once to {@link RunChain#proceed()}.
 * </p>
 */
public interface RunInterceptorExtension extends RuntimeExtension {

    /**
     * Wraps an assembly line run.
     *
     * @param pipeline pipeline being executed
     * @param request  per-run request
     * @param ctx      mutable context for the current run
     * @param chain    next element in the run chain
     * @param <IN>     pipeline input type
     * @param <OUT>    pipeline output type
     * @return the execution result returned by the delegate or produced by this
     *         interceptor
     */
    <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                             RunRequest request,
                                             ExecutionContext ctx,
                                             RunChain<IN, OUT> chain);

    /**
     * Continuation passed to an around-run interceptor.
     */
    @FunctionalInterface
    interface RunChain<IN, OUT> {
        /**
         * Executes the next interceptor or the runtime body.
         */
        ExecutionResult<OUT> proceed();
    }
}
