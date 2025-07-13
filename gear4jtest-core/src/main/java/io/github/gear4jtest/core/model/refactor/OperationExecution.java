package io.github.gear4jtest.core.model.refactor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OperationExecution {
    private Transformer<?, ?> operation;
    private OperationReport report;

    public OperationExecution(String id) {
        this.report = buildOperation(id);
    }

    public <T> OperationResult<T> complete(T object) {
        report.complete();
        return new OperationResult<>(object, report);
    }

    public <T> OperationResult<T> fail(Exception e) {
        report.fail(e);
        return new OperationResult<>(null, report);
    }

    public <T> OperationResult<T> stop(Exception e) {
        report.stop(e);
        return new OperationResult<>(null, report);
    }

    public <T> OperationResult<T> ignore(Exception e) {
        report.fail(e);
        return new OperationResult<>(null, report);
    }

    public <T extends Transformer<?, ?>> T getOperation() {
        return (T) operation;
    }

    void setOperation(Transformer<?, ?> operation) {
        this.operation = operation;
    }
    public OperationReport getReport() {
        return report;
    }
    public void setReport(OperationReport report) {
        this.report = report;
    }

    public OperationReport buildOperation(String operationId) {
        return new OperationReport(operationId);
    }

    public static class OperationReport {

        private UUID id;
        private String operationId;
        private Status status;
        private Instant startTime;
        private Instant endTime;
        private Exception error;
        private List<Exception> errorHandlerExceptions;
        private Map<String, Object> context;
        private List<OperationReport> subOperationReports;

        public OperationReport(String operationId) {
            this.id = UUID.randomUUID();
            this.operationId = operationId;
            this.startTime = Instant.now();
            this.subOperationReports = new ArrayList<>();
            this.errorHandlerExceptions = new ArrayList<>();
        }
        public void addSubOperationReport(OperationReport subReport) {
            subOperationReports.add(subReport);
        }

        public void complete() {
            this.status = Status.SUCCEEDED;
            this.endTime = Instant.now();
        }

        public void fail(Exception e) {
            this.error = e;
            this.status = Status.FAILED;
            this.endTime = Instant.now();
        }

        public void stop(Exception e) {
            this.error = e;
            this.status = Status.STOPPED;
            this.endTime = Instant.now();
        }

        public void ignore(Exception e) {
            this.error = e;
            this.status = Status.SKIPPED;
            this.endTime = Instant.now();
        }

        public void ignore() {
            this.status = Status.SKIPPED;
            this.endTime = Instant.now();
        }

        public Status getStatus() {
            return status;
        }
        public void setStatus(Status status) {
            this.status = status;
        }

        public Exception getError() {
            return error;
        }

        public List<OperationReport> getSubOperationReports() {
            return subOperationReports != null ? List.copyOf(subOperationReports) : List.of();
        }

        public UUID getId() {
            return id;
        }

        public String getOperationId() {
            return operationId;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public Instant getEndTime() {
            return endTime;
        }

        public void addErrorHandlerException(Exception e) {
            errorHandlerExceptions.add(e);
        }
        public Map<String, Object> getContext() {
            if (context == null) {
                context = new HashMap<>();
            }
            return context;
        }
        public List<Exception> getErrorHandlerExceptions() {
            return errorHandlerExceptions != null ? List.copyOf(errorHandlerExceptions) : List.of();
        }
        public enum Status {
            SKIPPED, SUCCEEDED, FAILED, STOPPED
        }
    }
}