package io.github.gear4jtest.core.execution;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;

public class InMemoryExecutionManager implements AssemblyRunManager {
    private final InMemoryAssemblyRunRepository repository;
    private final SensitiveDataRedactor redactor;

    public InMemoryExecutionManager() {
        this(InMemoryAssemblyRunRepository.INSTANCE, SensitiveDataRedactor.none());
    }

    public InMemoryExecutionManager(InMemoryAssemblyRunRepository repository) {
        this(repository, SensitiveDataRedactor.none());
    }

    public InMemoryExecutionManager(InMemoryAssemblyRunRepository repository, SensitiveDataRedactor redactor) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.redactor = redactor != null ? redactor : SensitiveDataRedactor.none();
    }

    @Override
    public void start(AssemblyRunTrace execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        repository.save(AssemblyRunRecord.from(execution, redactor));
    }

    @Override
    public void append(StationLogRecord record) {
        repository.saveOperationRecord(record != null ? record.redactedWith(redactor) : null);
    }

    @Override
    public void appendAll(List<StationLogRecord> records) {
        repository.saveOperationRecords(records == null ? null
                : records.stream().map(record -> record.redactedWith(redactor)).toList());
    }

    @Override
    public void end(AssemblyRunTrace finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        repository.update(AssemblyRunRecord.from(finalExecution, redactor));
    }
}
