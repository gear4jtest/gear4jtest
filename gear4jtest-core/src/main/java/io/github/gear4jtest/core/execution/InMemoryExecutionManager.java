package io.github.gear4jtest.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.persistence.AssemblyRun;

public class InMemoryExecutionManager implements AssemblyRunManager {

    @Override
    public void start(AssemblyRun execution) {
        InMemoryAssemblyRunRepository.INSTANCE.save(execution);
    }

    @Override
    public void append(StationLog record) {
        if (record == null) {
            return;
        }
        UUID id = UUID.fromString(record.getPipelineExecutionId());
        InMemoryAssemblyRunRepository.INSTANCE.findById(id).ifPresent(exec -> {
            List<StationLog> ops = exec.getOperations();
            if (ops == null) {
                ops = new ArrayList<>();
                exec.setOperations(ops);
            }
            ops.add(record);
        });
    }

    @Override
    public void appendAll(List<StationLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        records.forEach(this::append);
    }

    @Override
    public void end(AssemblyRun finalExecution) {
        InMemoryAssemblyRunRepository.INSTANCE.update(finalExecution);
    }
}
