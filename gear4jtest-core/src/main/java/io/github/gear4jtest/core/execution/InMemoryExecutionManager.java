package io.github.gear4jtest.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.persistence.StationLogSnapshot;

public class InMemoryExecutionManager implements AssemblyRunManager {

    @Override
    public void start(AssemblyRun execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        InMemoryAssemblyRunRepository.INSTANCE.save(execution);
    }

    @Override
    public void append(StationLogSnapshot record) {
        if (record == null) {
            return;
        }

        UUID runId = record.pipelineExecutionId();

        InMemoryAssemblyRunRepository.INSTANCE.findById(runId).ifPresent(execution -> {
            List<StationLog> operations = execution.getOperations();
            if (operations == null) {
                operations = new ArrayList<>();
                execution.setOperations(operations);
            }

            StationLog existing = null;
            for (StationLog operation : operations) {
                if (record.id().equals(operation.getId())) {
                    existing = operation;
                    break;
                }
            }

            if (existing == null) {
                operations.add(record.toStationLog());
            } else {
                StationLog detached = record.toStationLog();
                existing.setPipelineExecutionId(detached.getPipelineExecutionId());
                existing.setOperationId(detached.getOperationId());
                existing.setParentOperationId(detached.getParentOperationId());
                existing.setStatus(detached.getStatus());
                existing.setStartedAt(detached.getStartedAt());
                existing.setEndedAt(detached.getEndedAt());
                existing.setErrorMessage(detached.getErrorMessage());
                existing.setErrorHandlerMessages(detached.getErrorHandlerMessages());
                existing.setContext(detached.getContext());
                existing.setItemId(detached.getItemId());
            }
        });
    }

    @Override
    public void appendAll(List<StationLogSnapshot> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        for (StationLogSnapshot record : records) {
            append(record);
        }
    }

    @Override
    public void end(AssemblyRun finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        InMemoryAssemblyRunRepository.INSTANCE.update(finalExecution);
    }
}
