package io.github.gear4jtest.core.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.annotation.Internal;

@Internal
public abstract class AbstractStationLogState<T extends AbstractStationLogState<T, S>, S> {
    private transient Object output;
    private final transient List<Throwable> throwables = Collections.synchronizedList(new ArrayList<>());
    private UUID id;
    private UUID pipelineExecutionId;
    private String operationId;
    private UUID parentOperationId;
    private String branchId;
    private S status;
    private Instant startedAt;
    private Instant endedAt;
    private String errorMessage;
    private String errorHandlerMessages;
    private Map<String, Object> context;
    private List<T> subOperations = Collections.synchronizedList(new ArrayList<>());
    private String itemId;

    protected AbstractStationLogState() {
    }

    protected abstract S runningStatus();

    protected abstract S skippedStatus();

    protected abstract S succeededStatus();

    protected abstract S failedStatus();

    protected abstract S stoppedStatus();

    protected abstract S cancelledStatus();

    protected void initializeStarted(UUID pipelineExecutionId, String operationId, UUID parentOperationId) {
        this.id = UUID.randomUUID();
        this.pipelineExecutionId = pipelineExecutionId;
        this.operationId = operationId;
        this.parentOperationId = parentOperationId;
        this.status = runningStatus();
        this.startedAt = Instant.now();
        this.context = new HashMap<>();
    }

    public void markSuccess(Object output) {
        this.status = succeededStatus();
        this.endedAt = Instant.now();
        this.output = output;
    }

    public void markFailed(Exception e) {
        this.status = failedStatus();
        this.endedAt = Instant.now();
        this.errorMessage = e != null ? e.getMessage() : null;
        addErrorHandlerException(e);
    }

    public void markCancelled(Exception e) {
        this.status = cancelledStatus();
        this.endedAt = Instant.now();
        this.errorMessage = e != null ? e.getMessage() : null;
        addErrorHandlerException(e);
    }

    public void markStopped(Exception e) {
        this.status = stoppedStatus();
        this.endedAt = Instant.now();
        this.errorMessage = e != null ? e.getMessage() : null;
        addErrorHandlerException(e);
    }

    public void markSkipped() {
        this.status = skippedStatus();
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

    public synchronized void addErrorHandlerException(Exception e) {
        if (e == null) {
            return;
        }
        String msg = e.getMessage();
        if (msg == null) {
            return;
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

    public void setId(UUID id) {
        this.id = id;
    }

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

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public S getStatus() {
        return status;
    }

    public void setStatus(S status) {
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

    public List<T> getSubOperations() {
        return Collections.unmodifiableList(subOperations);
    }

    public void setSubOperations(List<T> subOperations) {
        this.subOperations = new ArrayList<>(subOperations == null ? List.of() : subOperations);
    }

    @SuppressWarnings("unchecked")
    public <R> R getOutput() {
        return (R) output;
    }

    public void setOutput(Object output) {
        this.output = output;
    }

    public List<Throwable> getThrowables() {
        synchronized (throwables) {
            return List.copyOf(throwables);
        }
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
}
