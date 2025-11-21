package io.github.gear4jtest.core.model.refactor;

import java.util.UUID;

public class ExecutionResult<T> {
    private final UUID executionId;
    private final T result;
    private final boolean success;
    private final Exception error;
    
    
    public ExecutionResult(UUID executionId, T result, boolean success, Exception error) {
        this.executionId = executionId;
        this.result = result;
        this.success = success;
        this.error = error;
        
    }

    public UUID getExecutionId() { return executionId; }
    public T getResult() { return result; }
    public boolean isSuccess() { return success; }
    public Exception getError() { return error; }
    
}