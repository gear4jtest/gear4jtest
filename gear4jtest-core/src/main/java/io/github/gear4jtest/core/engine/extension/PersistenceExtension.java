package io.github.gear4jtest.core.engine.extension;

import java.time.Instant;

import io.github.gear4jtest.core.engine.core.RunRequest;
import io.github.gear4jtest.core.engine.spi.AbstractRunHooksExtension;
import io.github.gear4jtest.core.engine.spi.PersistingStationRunner;
import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.engine.spi.StationWrapperExtension;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ExecutionContext;
import io.github.gear4jtest.core.model.ExecutionResult;
import io.github.gear4jtest.core.persistence.ExecutionStatus;

public class PersistenceExtension extends AbstractRunHooksExtension implements StationWrapperExtension {

    private final AssemblyRunManager manager;

    public PersistenceExtension(AssemblyRunManager manager) {
        this.manager = manager;
    }

    @Override
    protected void onStart(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx) {
        manager.start(ctx.getPipelineExecution());
    }

    @Override
    protected void onResult(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx, ExecutionResult<?> result) {
        ctx.getPipelineExecution().setResult(result.getResult());
    }

    @Override
    protected void onEnd(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx) {
        ctx.getPipelineExecution().setContext(ctx.getContext());
        ctx.getPipelineExecution().setEndTime(Instant.now());
        ctx.getPipelineExecution().setStatus(ExecutionStatus.SUCCEEDED);
        manager.end(ctx.getPipelineExecution());
    }

    @Override
    public StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx) {
        return new PersistingStationRunner(delegate, manager);
    }
}