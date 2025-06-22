package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.gear4jtest.core.model.refactor.OperationExecution.OperationReport.*;

public class OperationExecutionRecord {
    private String id;
    private String pipelineExecutionId;
    private String operationId;
    private String parentOperationId;
    private Status status;
    private Instant startTime;
    private Instant endTime;
    private String errorMessage;
    private String errorHandlerMessages;
    private Map<String, Object> context;
    private List<OperationExecutionRecord> subOperations = new ArrayList<>();

    public OperationExecutionRecord() {
    }

    public OperationExecutionRecord(String id,
                                    String pipelineExecutionId,
                                    String operationId,
                                    String parentOperationId,
                                    Status status,
                                    Instant startTime,
                                    Instant endTime,
                                    String errorMessage,
                                    String errorHandlerMessages,
                                    Map<String, Object> context,
                                    List<OperationExecutionRecord> subOperations) {
        this.id = id;
        this.pipelineExecutionId = pipelineExecutionId;
        this.operationId = operationId;
        this.parentOperationId = parentOperationId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.errorMessage = errorMessage;
        this.errorHandlerMessages = errorHandlerMessages;
        this.context = context;
        this.subOperations = subOperations;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPipelineExecutionId() {
        return pipelineExecutionId;
    }

    public void setPipelineExecutionId(String pipelineExecutionId) {
        this.pipelineExecutionId = pipelineExecutionId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getParentOperationId() {
        return parentOperationId;
    }

    public void setParentOperationId(String parentOperationId) {
        this.parentOperationId = parentOperationId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
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

    public List<OperationExecutionRecord> getSubOperations() {
        return subOperations;
    }

    public void setSubOperations(List<OperationExecutionRecord> subOperations) {
        this.subOperations = subOperations;
    }
    public String getErrorHandlerMessages() {
        return errorHandlerMessages;
    }
    public void setErrorHandlerMessages(String errorHandlerMessages) {
        this.errorHandlerMessages = errorHandlerMessages;
    }
    public Map<String, Object> getContext() {
        return context;
    }
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
}