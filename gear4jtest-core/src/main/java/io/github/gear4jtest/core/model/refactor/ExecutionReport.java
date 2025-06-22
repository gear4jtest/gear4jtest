package io.github.gear4jtest.core.model.refactor;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

import static io.github.gear4jtest.core.model.refactor.OperationExecution.*;

public class ExecutionReport {
    private final List<OperationReport> operations = new ArrayList<>();
    private final Instant startTime;
    private Instant endTime;
    private boolean shouldStop;
    private boolean fatal;
    
    public ExecutionReport() { this.startTime = Instant.now(); }

    public void addOperationReport(OperationReport operationReport) {
        operations.add(operationReport);
    }
    
    public void complete() { this.endTime = Instant.now(); }
    public List<OperationReport> getOperations() { return List.copyOf(operations); }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public boolean isShouldStop() { return shouldStop; }
    public void setShouldStop(boolean shouldStop) { this.shouldStop = shouldStop; }
    public boolean isFatal() { return fatal; }
    public void setFatal(boolean fatal) { this.fatal = fatal; }
}