package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.persistence.PipelineExecution;

public class ExecutionResult<T> {
    private final T result;
    private final boolean success;
    private final PipelineExecution execution;
    private final Exception error;
    
    
    public ExecutionResult(T result, boolean success, PipelineExecution execution, Exception error) {
        this.result = result;
        this.success = success;
        this.execution = execution;
        this.error = error;
        
    }

    public T getResult() { return result; }
    public boolean isSuccess() { return success; }
    public PipelineExecution getExecution() {
        return this.execution;
    }
    public Exception getError() { return error; }

    public static <OUT> ExecutionResult<OUT> success(OUT result, PipelineExecution exec) {
        return new ExecutionResult<>(result, true, exec, null);
    }

    public static <OUT> ExecutionResult<OUT> failure(Exception message, PipelineExecution exec) {
        return new ExecutionResult<>(null, false, exec, message);
    }
}