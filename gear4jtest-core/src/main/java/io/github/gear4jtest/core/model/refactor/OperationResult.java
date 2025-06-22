package io.github.gear4jtest.core.model.refactor;

public class OperationResult<T> {
    private final T result;
    private final OperationExecution.OperationReport report;

    public OperationResult(T result, OperationExecution.OperationReport report) {
        this.result = result;
        this.report = report;
    }
    
    public T getResult() { return result; }
    public OperationExecution.OperationReport getReport() { return report; }
    public boolean isSuccess() {
        return report.getStatus() == OperationExecution.OperationReport.Status.SUCCEEDED;
    }

    public Exception getError() {
        return report.getError();
    }
}