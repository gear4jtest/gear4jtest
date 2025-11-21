package io.github.gear4jtest.core.execution;

import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.DatabasePipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class DatabaseExecutionManager implements PipelineExecutionManager {

    private final DatabasePipelineExecutionRepository repository;

    public DatabaseExecutionManager(DataSource dataSource) {
        this.repository = new DatabasePipelineExecutionRepository(dataSource);
        this.repository.initialize();
    }

    @Override
    public void start(PipelineExecution execution) {
        repository.save(execution);
    }

    @Override
    public void append(OperationExecutionRecord rec) {
        repository.saveOperation(rec);
    }

    @Override
    public void end(PipelineExecution finalExecution) {
        repository.update(finalExecution);
    }
}
