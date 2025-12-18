package io.github.gear4jtest.core.model;

import io.github.gear4jtest.core.persistence.StationLog;

public class StationChainResult<OUT> {

    private final OUT result;
    private final StationLog lastRecord;
    private final boolean success;

    public StationChainResult(OUT result,
                              StationLog lastRecord,
                              boolean success) {
        this.result = result;
        this.lastRecord = lastRecord;
        this.success = success;
    }

    public OUT getResult() {
        return result;
    }

    public StationLog getLastRecord() {
        return lastRecord;
    }

    public boolean isSuccess() {
        return success;
    }
}
