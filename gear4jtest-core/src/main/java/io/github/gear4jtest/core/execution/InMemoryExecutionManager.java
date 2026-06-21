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

    public static Builder builder() {
        return new Builder();
    }

    private InMemoryExecutionManager(Builder builder) {
        this.repository = Objects.requireNonNull(builder.repository, "repository must not be null");
        this.redactor = builder.redactor != null ? builder.redactor : SensitiveDataRedactor.none();
    }

    public static final class Builder {
        private InMemoryAssemblyRunRepository repository = new InMemoryAssemblyRunRepository();
        private SensitiveDataRedactor redactor = SensitiveDataRedactor.none();

        private Builder() {
        }

        public Builder repository(InMemoryAssemblyRunRepository repository) {
            this.repository = repository;
            return this;
        }

        public Builder redactor(SensitiveDataRedactor redactor) {
            this.redactor = redactor;
            return this;
        }

        public InMemoryExecutionManager build() {
            return new InMemoryExecutionManager(this);
        }
    }

    @Override
    public void start(AssemblyRunTrace execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        repository.save(AssemblyRunRecord.from(execution, redactor));
    }

    @Override
    public void append(StationLogRecord stationLogRecord) {
        repository.saveOperationRecord(stationLogRecord != null ? stationLogRecord.redactedWith(redactor) : null);
    }

    @Override
    public void appendAll(List<StationLogRecord> records) {
        repository.saveOperationRecords(records == null ? null
                : records.stream().map(entry -> entry.redactedWith(redactor)).toList());
    }

    @Override
    public void end(AssemblyRunTrace finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        repository.update(AssemblyRunRecord.from(finalExecution, redactor));
    }
}
