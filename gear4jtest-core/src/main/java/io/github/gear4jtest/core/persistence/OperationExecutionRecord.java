package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Représente l'exécution d'une opération dans un pipeline.
 * Sert à la fois de modèle runtime et de modèle de persistance.
 */
public class OperationExecutionRecord {

    public enum Status {
        SKIPPED, SUCCEEDED, FAILED, STOPPED
    }

    private transient Object output;
    private transient List<Throwable> throwables;

    private String id;
    private String pipelineExecutionId;
    private String operationId;
    private String parentOperationId;
    private Status status;
    private Instant startedAt;
    private Instant endedAt;
    private String errorMessage;
    private String errorHandlerMessages;
    private Map<String, Object> context;
    private List<OperationExecutionRecord> subOperations = new ArrayList<>();

    // ---------- Fabrication ----------

    public static OperationExecutionRecord start(String pipelineExecutionId,
                                                 String operationId,
                                                 String parentOperationId) {
        OperationExecutionRecord record = new OperationExecutionRecord();
        record.id = UUID.randomUUID().toString();
        record.pipelineExecutionId = pipelineExecutionId;
        record.operationId = operationId;
        record.parentOperationId = parentOperationId;
        record.status = Status.SKIPPED; // par défaut
        record.startedAt = Instant.now();
        return record;
    }

    // ---------- Lifecycle helpers ----------

    public void markSuccess(Object output) {
        this.status = Status.SUCCEEDED;
        this.endedAt = Instant.now();
        this.output = output;
    }

    public void markFailed(Exception e) {
        this.status = Status.FAILED;
        this.endedAt = Instant.now();
        this.errorMessage = e != null ? e.getMessage() : null;
        addErrorHandlerException(e);
    }

    public void markStopped(Exception e) {
        this.status = Status.STOPPED;
        this.endedAt = Instant.now();
        this.errorMessage = e != null ? e.getMessage() : null;
        addErrorHandlerException(e);
    }

    public void markSkipped() {
        this.status = Status.SKIPPED;
        this.endedAt = Instant.now();
    }

    public void markSkipped(Exception e) {
        this.status = Status.SKIPPED;
        this.endedAt = Instant.now();
        this.errorMessage = e != null ? e.getMessage() : null;
        addErrorHandlerException(e);
    }

    public void addErrorHandlerException(Exception e) {
        if (e == null) return;
        String msg = e.getMessage();
        if (msg == null) return;
        if (this.throwables == null) {
            this.throwables = new ArrayList<>();
        }
        this.throwables.add(e);
        if (this.errorHandlerMessages == null || this.errorHandlerMessages.isBlank()) {
            this.errorHandlerMessages = msg;
        } else {
            this.errorHandlerMessages = this.errorHandlerMessages + ", " + msg;
        }
    }

    public void addSubOperation(OperationExecutionRecord child) {
        if (child == null) return;
        child.setParentOperationId(this.id);
        if (subOperations == null) {
            subOperations = new ArrayList<>();
        }
        subOperations.add(child);
    }

    // ---------- Getters / setters ----------

    public String getId() {
        return id;
    }

    public void setId(String id) { this.id = id; }

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

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public List<OperationExecutionRecord> getSubOperations() {
        return subOperations;
    }

    public void setSubOperations(List<OperationExecutionRecord> subOperations) {
        this.subOperations = subOperations;
    }

    @SuppressWarnings("unchecked")
    public <T> T getOutput(Class<T> type) {
        return (T) output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public List<Throwable> getThrowables() {
        return throwables;
    }
}
