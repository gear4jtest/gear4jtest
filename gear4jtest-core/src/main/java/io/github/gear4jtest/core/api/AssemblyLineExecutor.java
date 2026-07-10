package io.github.gear4jtest.core.api;

/**
 * Public execution entry point for Gear4J pipelines.
 *
 * <p>
 * Applications can depend on this interface instead of a concrete engine when
 * they need to execute pipelines or mock the engine in tests.
 * </p>
 */
public interface AssemblyLineExecutor {
    /**
     * Executes the provided pipeline with the provided run request.
     *
     * @param pipeline pipeline definition to execute
     * @param request  per-run input, context and runtime overrides
     * @param <IN>     pipeline input type
     * @param <OUT>    pipeline output type
     * @return execution result including the user result and runtime trace
     */
    <IN, OUT> ExecutionResult<OUT> execute(AssemblyLine<IN, OUT> pipeline, RunRequest<IN> request);
}
