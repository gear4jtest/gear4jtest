package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PipelineExecution {
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
    private List<OperationExecutionRecord> operations = new ArrayList<>();

    private UUID parentExecutionId; // null pour la top-level
    private UUID rootExecutionId;   // id de la top-level

    public PipelineExecution() {
    }

    public PipelineExecution(UUID id, String pipelineId, Map<String, Object> pipelineParams) {
        this.id = id;
        this.pipelineId = pipelineId;
        this.startTime = Instant.now();
        this.status = ExecutionStatus.RUNNING;
        this.setInputParams(pipelineParams);
    }

    public static PipelineExecution childOf(PipelineExecution parent, String assemblyLineId) {
        PipelineExecution child = new PipelineExecution();
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
        this.endTime = Instant.now();
        // sérialiser result si besoin
    }

    public void markFailed(Throwable t) {
        this.status = ExecutionStatus.FAILED;
        this.endTime = Instant.now();
        // sérialiser t sous forme de message si besoin
    }

    // Getters/Setters
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

    public List<OperationExecutionRecord> getOperations() {
        return operations;
    }

    public void setOperations(List<OperationExecutionRecord> operations) {
        this.operations = operations;
    }

    public UUID getParentExecutionId() {
        return parentExecutionId;
    }

    public UUID getRootExecutionId() {
        return rootExecutionId;
    }
    //    public void complete(){this.endTime=Instant.now();this.status=ExecutionStatus.COMPLETED;}
//    public void fail(Exception error){this.endTime=Instant.now();this.status=ExecutionStatus.FAILED;setError(error);}
}
