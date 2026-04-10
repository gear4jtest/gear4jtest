package io.github.gear4jtest.core.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StationLog {

    public enum Status {
        RUNNING, SKIPPED, SUCCEEDED, FAILED, STOPPED, CANCELLED
    }

    private transient Object output;
    private transient List<Throwable> throwables;

    private UUID id;
    private UUID pipelineExecutionId;
    private String operationId;
    private UUID parentOperationId;
    private Status status;
    private Instant startedAt;
    private Instant endedAt;
    private String errorMessage;
    private String errorHandlerMessages;
    private Map<String, Object> context;
    private List<StationLog> subOperations = Collections.synchronizedList(new ArrayList<>());
    private String itemId;

    public static StationLog start(UUID pipelineExecutionId,
                                   String operationId,
                                   UUID parentOperationId) {
        StationLog record = new StationLog();
        record.id = UUID.randomUUID();
        record.pipelineExecutionId = pipelineExecutionId;
        record.operationId = operationId;
        record.parentOperationId = parentOperationId;
        record.status = Status.RUNNING;
        record.startedAt = Instant.now();
        record.context = new HashMap<>();
        return record;
    }

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

    public void markCancelled(Exception e) {
        this.status = Status.CANCELLED;
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
        markSkipped();
        this.errorMessage = e != null ? e.getMessage() : null;
        addErrorHandlerException(e);
    }

    public void markSkipped(String reason) {
        markSkipped();
        if (reason != null) {
            ensureContext().put("skip.reason", reason);
        }
    }

    public void addErrorHandlerException(Exception e) {
        if (e == null) {
            return;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return;
        }
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

    private Map<String, Object> ensureContext() {
        if (this.context == null) {
            this.context = new HashMap<>();
        }
        return this.context;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) { this.id = id; }

    public UUID getPipelineExecutionId() {
        return pipelineExecutionId;
    }

    public void setPipelineExecutionId(UUID pipelineExecutionId) {
        this.pipelineExecutionId = pipelineExecutionId;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public UUID getParentOperationId() {
        return parentOperationId;
    }

    public void setParentOperationId(UUID parentOperationId) {
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

    public List<StationLog> getSubOperations() {
        return Collections.unmodifiableList(subOperations);
    }

    public void setSubOperations(List<StationLog> subOperations) {
        this.subOperations = new ArrayList<>(subOperations == null ? List.of() : subOperations);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOutput() {
        return (T) output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public List<Throwable> getThrowables() {
        return throwables;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
}
