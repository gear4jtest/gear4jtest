package io.github.gear4jtest.core.builtin.extension;

import java.time.Instant;

import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.builtin.runner.PersistingStationRunner;
import io.github.gear4jtest.core.spi.extension.AbstractRunHooksExtension;
import io.github.gear4jtest.core.spi.extension.StationWrapperExtension;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.ExecutionResult;

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
        if (ctx.getPipelineExecution().getEndTime() == null) {
            ctx.getPipelineExecution().setEndTime(Instant.now());
        }
        manager.end(ctx.getPipelineExecution());
    }

    @Override
    public StationRunner wrapStationRunner(StationRunner delegate, ExecutionContext ctx) {
        return new PersistingStationRunner(delegate, manager);
    }
}
