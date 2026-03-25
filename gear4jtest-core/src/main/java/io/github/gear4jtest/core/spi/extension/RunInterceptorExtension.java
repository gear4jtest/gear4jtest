package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;

public interface RunInterceptorExtension extends RuntimeExtension {
    <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline, RunRequest request, ExecutionContext ctx, RunChain<IN, OUT> chain);

    @FunctionalInterface
    interface RunChain<IN, OUT> {
        ExecutionResult<OUT> proceed();
    }
}
