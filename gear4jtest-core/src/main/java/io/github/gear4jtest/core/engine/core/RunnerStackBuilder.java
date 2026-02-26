package io.github.gear4jtest.core.engine.core;

import io.github.gear4jtest.core.engine.spi.StationRunner;
import io.github.gear4jtest.core.model.AssemblyLine;
import io.github.gear4jtest.core.model.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunnerStackBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunnerStackBuilder.class);

    private final StrategyRegistry strategyRegistry;

    public RunnerStackBuilder(StrategyRegistry strategyRegistry) {
        this.strategyRegistry = strategyRegistry;
    }

    public StationRunner build(AssemblyLine<?, ?> pipeline, RunRequest request, ExecutionContext ctx, RunPlan plan) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Building station runner stack. pipelineId={}, stationWrappers={}, runInterceptors={}, executorWrappers={}",
                    pipeline.getId(),
                    plan.stationWrappers().size(),
                    plan.runInterceptors().size(),
                    plan.executorWrappers().size());
        }

        TerminalStationRunner terminalStationRunner = new TerminalStationRunner(strategyRegistry);
        StationRunner stack = terminalStationRunner;

        var wrappers = plan.stationWrappers();
        for (int i = wrappers.size() - 1; i >= 0; i--) {
            stack = wrappers.get(i).wrapStationRunner(stack, ctx);
        }

        StationRunner rootRunner = new ScopeInitializingRunner(stack);

        terminalStationRunner.setRootRunner(rootRunner);

        return rootRunner;
    }
}
