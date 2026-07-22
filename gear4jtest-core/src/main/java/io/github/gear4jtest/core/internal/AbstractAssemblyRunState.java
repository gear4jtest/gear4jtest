package io.github.gear4jtest.core.internal;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.Internal;
import io.github.gear4jtest.core.persistence.ExecutionStatus;

@Internal
public abstract class AbstractAssemblyRunState {
    private UUID id;
    private String assemblyLineId;
    private Map<String, Object> context;
    private Object inputParams;
    private Object result;
    private ExecutionStatus status;
    private Instant startTime;
    private Instant endTime;
    private String errorMessage;
    private Exception error;
    private UUID parentExecutionId;
    private UUID rootExecutionId;
    private UUID parentStationLogId;

    protected AbstractAssemblyRunState() {
    }

    protected AbstractAssemblyRunState(UUID id, String assemblyLineId, Map<String, Object> pipelineParams) {
        this.id = id;
        this.assemblyLineId = assemblyLineId;
        this.status = ExecutionStatus.RUNNING;
        this.inputParams = pipelineParams;
    }

    public void markStarted() {
        this.status = ExecutionStatus.RUNNING;
        this.startTime = Instant.now();
    }

    public void markSuccess(Object result) {
        this.status = ExecutionStatus.SUCCEEDED;
        this.result = result;
        this.endTime = Instant.now();
    }

    public void markFailed(Throwable t) {
        this.status = ExecutionStatus.FAILED;
        this.endTime = Instant.now();
        if (t instanceof Exception exception) {
            setError(exception);
        } else if (t != null) {
            this.errorMessage = t.getMessage();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAssemblyLineId() {
        return assemblyLineId;
    }

    public void setAssemblyLineId(String assemblyLineId) {
        this.assemblyLineId = assemblyLineId;
    }

    public Map<String, Object> getContext() {
        return context == null ? null : Collections.unmodifiableMap(context);
    }

    public Map<String, Object> mutableContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public Object getInputParams() {
        return inputParams;
    }

    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Exception getError() {
        return error;
    }

    public void setError(Exception error) {
        this.error = error;
        this.errorMessage = error != null ? error.getMessage() : null;
    }

    public UUID getParentExecutionId() {
        return parentExecutionId;
    }

    public void setParentExecutionId(UUID parentExecutionId) {
        this.parentExecutionId = parentExecutionId;
    }

    public UUID getRootExecutionId() {
        return rootExecutionId;
    }

    public void setRootExecutionId(UUID rootExecutionId) {
        this.rootExecutionId = rootExecutionId;
    }

    public UUID getParentStationLogId() {
        return parentStationLogId;
    }

    public void setParentStationLogId(UUID parentStationLogId) {
        this.parentStationLogId = parentStationLogId;
    }
}
