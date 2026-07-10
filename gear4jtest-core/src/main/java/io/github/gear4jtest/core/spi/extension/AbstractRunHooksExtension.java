package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;

/**
 * Convenience base class for writing run extensions as lifecycle hooks.
 *
 * <p>
 * The runtime still sees this class as a {@link RunInterceptorExtension}.
 * Subclasses override only the hook methods they need while this base class
 * handles delegation to the next run chain element.
 * </p>
 */
public abstract class AbstractRunHooksExtension implements RunInterceptorExtension {
    @Override
    public final <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline,
                                                          RunRequest<IN> request,
                                                          ExecutionContext ctx,
                                                          RunChain<IN, OUT> chain) {
        onStart(pipeline, request, ctx);

        try {
            ExecutionResult<OUT> result = chain.proceed();
            onResult(pipeline, request, ctx, result);
            return result;
        } catch (RuntimeException e) {
            onException(pipeline, request, ctx, e);
            throw e;
        } finally {
            onEnd(pipeline, request, ctx);
        }
    }

    /**
     * Hook called before the wrapped run proceeds.
     */
    protected void onStart(AssemblyLine<?, ?> pipeline, RunRequest<?> request, ExecutionContext ctx) {
    }

    /**
     * Hook called when the wrapped run returns an {@link ExecutionResult}.
     *
     * <p>
     * This hook is also called when the result represents a normalized functional
     * failure.
     * </p>
     */
    protected void onResult(AssemblyLine<?, ?> pipeline,
                            RunRequest<?> request,
                            ExecutionContext ctx,
                            ExecutionResult<?> result) {
    }

    /**
     * Hook called only when the wrapped run throws a runtime exception.
     */
    protected void onException(AssemblyLine<?, ?> pipeline,
                               RunRequest<?> request,
                               ExecutionContext ctx,
                               RuntimeException error) {
    }

    /**
     * Hook called in the interceptor {@code finally} block.
     */
    protected void onEnd(AssemblyLine<?, ?> pipeline, RunRequest<?> request, ExecutionContext ctx) {
    }
}
