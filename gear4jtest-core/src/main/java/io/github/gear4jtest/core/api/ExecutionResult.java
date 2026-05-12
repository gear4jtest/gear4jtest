package io.github.gear4jtest.core.api;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;

/**
 * Public result returned after a pipeline execution completes.
 *
 * <p>
 * The result separates the user-facing output from the execution trace. A
 * failed result represents a normalized pipeline failure. Fatal JVM errors are
 * not expected to be converted into this type.
 * </p>
 */
public class ExecutionResult<T> {
    private final T result;
    private final boolean success;
    private final AssemblyRunTrace execution;
    private final Exception error;

    public ExecutionResult(T result, boolean success, AssemblyRunTrace execution, Exception error) {
        this.result = result;
        this.success = success;
        this.execution = execution;
        this.error = error;
    }

    /**
     * Creates a successful execution result.
     */
    public static <OUT> ExecutionResult<OUT> success(OUT result, AssemblyRunTrace exec) {
        return new ExecutionResult<>(result, true, exec, null);
    }

    /**
     * Creates a failed execution result from a normalized exception.
     */
    public static <OUT> ExecutionResult<OUT> failure(Exception message, AssemblyRunTrace exec) {
        return new ExecutionResult<>(null, false, exec, message);
    }

    public T getResult() {
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public AssemblyRunTrace getExecution() {
        return this.execution;
    }

    public Exception getError() {
        return error;
    }
}
