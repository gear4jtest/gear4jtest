package io.github.gear4jtest.core.model.refactor;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

public class OperationChainResult<OUT> {

    private final OUT result;
    private final OperationExecutionRecord lastRecord;
    private final boolean success;

    public OperationChainResult(OUT result,
                                OperationExecutionRecord lastRecord,
                                boolean success) {
        this.result = result;
        this.lastRecord = lastRecord;
        this.success = success;
    }

    public OUT getResult() {
        return result;
    }

    public OperationExecutionRecord getLastRecord() {
        return lastRecord;
    }

    public boolean isSuccess() {
        return success;
    }
}
