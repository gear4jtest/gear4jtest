package io.github.gear4jtest.core.engine;

import java.util.List;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.support.ExecutionSupport;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension;
import io.github.gear4jtest.core.spi.extension.RunInterceptorExtension.RunChain;
import io.github.gear4jtest.core.spi.runner.StationRunner;

final class AssemblyLineRootExecutionChain {
    private final RunnerChainFactory runnerChainFactory;

    AssemblyLineRootExecutionChain(RunnerChainFactory runnerChainFactory) {
        this.runnerChainFactory = runnerChainFactory;
    }

    <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline,
                                           RunRequest request,
                                           ExecutionContext context,
                                           ExecutionSupport support,
                                           ResolvedExtensions resolvedExtensions,
                                           AssemblyRunTrace execution) {
        StationRunner rootRunner = runnerChainFactory.createRootRunner(pipeline, request, context, resolvedExtensions);
        StationExecutionContext rootContext = new DefaultStationExecutionContext("root-invoker", context, support);

        List<RunInterceptorExtension> interceptors = resolvedExtensions.runInterceptors();
        RunChain<IN, OUT> chain = () -> AssemblyLineExecutionResultMapper.executeRootStation(pipeline, request,
                                                                                             rootRunner,
                                                                                             rootContext, execution);

        for (int i = interceptors.size() - 1; i >= 0; i--) {
            RunInterceptorExtension interceptor = interceptors.get(i);
            RunChain<IN, OUT> next = chain;
            chain = () -> interceptor.aroundRun(pipeline, request, context, next);
        }

        return chain.proceed();
    }
}
