package io.github.gear4jtest.core.execution;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLogRecord;

public class InMemoryExecutionManager implements AssemblyRunManager {
    private final InMemoryAssemblyRunRepository repository;

    public InMemoryExecutionManager() {
        this(InMemoryAssemblyRunRepository.INSTANCE);
    }

    public InMemoryExecutionManager(InMemoryAssemblyRunRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public void start(AssemblyRunTrace execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        repository.save(AssemblyRunRecord.from(execution));
    }

    @Override
    public void append(StationLogRecord record) {
        repository.saveOperationRecord(record);
    }

    @Override
    public void appendAll(List<StationLogRecord> records) {
        repository.saveOperationRecords(records);
    }

    @Override
    public void end(AssemblyRunTrace finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        repository.update(AssemblyRunRecord.from(finalExecution));
    }
}
