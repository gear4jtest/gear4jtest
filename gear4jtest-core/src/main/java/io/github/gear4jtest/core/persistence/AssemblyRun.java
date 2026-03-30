package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AssemblyRun {
    private UUID id;
    private String pipelineId;
    private Map<String, Object> context;
    private Object inputParams;
    private Object result;
    private ExecutionStatus status;
    private Instant startTime;
    private Instant endTime;
    private String errorMessage;
    private Exception error;
    private List<StationLog> operations = new ArrayList<>();

    private UUID parentExecutionId;
    private UUID rootExecutionId;

    public AssemblyRun() {
    }

    public AssemblyRun(UUID id, String pipelineId, Map<String, Object> pipelineParams) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.status = ExecutionStatus.RUNNING;
        this.setInputParams(pipelineParams);
    }

    public static AssemblyRun childOf(AssemblyRun parent, String assemblyLineId) {
        AssemblyRun child = new AssemblyRun();
        child.id = UUID.randomUUID();
        child.pipelineId = assemblyLineId;
        child.parentExecutionId = parent.getId();
        child.rootExecutionId = parent.getRootExecutionId() != null ? parent.getRootExecutionId() : parent.getId();
        child.markStarted();
        return child;
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

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public Map<String, Object> getContext() {
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

    public List<StationLog> getOperations() {
        return operations;
    }

    public void setOperations(List<StationLog> operations) {
        this.operations = operations;
    }

    public UUID getParentExecutionId() {
        return parentExecutionId;
    }

    public UUID getRootExecutionId() {
        return rootExecutionId;
    }
}
