package io.github.gear4jtest.core.engine.spi;

import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.ExecutionResult;
import io.github.gear4jtest.core.engine.core.RunRequest;

public interface RunInterceptorExtension extends RuntimeExtension {
    <IN, OUT> ExecutionResult<OUT> aroundRun(AssemblyLine<IN, OUT> pipeline, RunRequest request, ExecutionContext ctx, RunChain<IN, OUT> chain);

    @FunctionalInterface
    interface RunChain<IN, OUT> {
        ExecutionResult<OUT> proceed();
    }
}
