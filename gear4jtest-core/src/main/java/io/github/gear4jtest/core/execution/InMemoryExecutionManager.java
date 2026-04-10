package io.github.gear4jtest.core.execution;

import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLogSnapshot;

public class InMemoryExecutionManager implements AssemblyRunManager {

    @Override
    public void start(AssemblyRun execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        InMemoryAssemblyRunRepository.INSTANCE.save(execution);
    }

    @Override
    public void append(StationLogSnapshot record) {
        InMemoryAssemblyRunRepository.INSTANCE.saveOperationSnapshot(record);
    }

    @Override
    public void appendAll(List<StationLogSnapshot> records) {
        InMemoryAssemblyRunRepository.INSTANCE.saveOperationSnapshots(records);
    }

    @Override
    public void end(AssemblyRun finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        InMemoryAssemblyRunRepository.INSTANCE.update(finalExecution);
    }
}
