package io.github.gear4jtest.core.api;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;

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

    public T getResult() { return result; }
    public boolean isSuccess() { return success; }
    public AssemblyRunTrace getExecution() {
        return this.execution;
    }
    public Exception getError() { return error; }

    public static <OUT> ExecutionResult<OUT> success(OUT result, AssemblyRunTrace exec) {
        return new ExecutionResult<>(result, true, exec, null);
    }

    public static <OUT> ExecutionResult<OUT> failure(Exception message, AssemblyRunTrace exec) {
        return new ExecutionResult<>(null, false, exec, message);
    }
}
