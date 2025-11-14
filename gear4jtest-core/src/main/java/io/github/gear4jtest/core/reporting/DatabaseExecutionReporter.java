package io.github.gear4jtest.core.reporting;

import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.DatabasePipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class DatabaseExecutionReporter implements ExecutionReporter {

    private final DatabasePipelineExecutionRepository repository;

    public DatabaseExecutionReporter(DataSource dataSource) {
        this.repository = new DatabasePipelineExecutionRepository(dataSource);
        this.repository.initialize();
    }

    @Override
    public void onPipelineStart(PipelineExecution execution) {
        repository.save(execution);
    }

    @Override
    public void onPipelineUpdate(PipelineExecution execution) {
        repository.update(execution);
    }

    @Override
    public void onPipelineEnd(PipelineExecution execution) {
        repository.update(execution);
    }
}
