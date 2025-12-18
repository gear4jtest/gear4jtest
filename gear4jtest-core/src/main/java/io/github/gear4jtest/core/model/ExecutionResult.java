package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.persistence.AssemblyRun;

public class ExecutionResult<T> {
    private final T result;
    private final boolean success;
    private final AssemblyRun execution;
    private final Exception error;
    
    
    public ExecutionResult(T result, boolean success, AssemblyRun execution, Exception error) {
        this.result = result;
        this.success = success;
        this.execution = execution;
        this.error = error;
        
    }

    public T getResult() { return result; }
    public boolean isSuccess() { return success; }
    public AssemblyRun getExecution() {
        return this.execution;
    }
    public Exception getError() { return error; }

    public static <OUT> ExecutionResult<OUT> success(OUT result, AssemblyRun exec) {
        return new ExecutionResult<>(result, true, exec, null);
    }

    public static <OUT> ExecutionResult<OUT> failure(Exception message, AssemblyRun exec) {
        return new ExecutionResult<>(null, false, exec, message);
    }
}