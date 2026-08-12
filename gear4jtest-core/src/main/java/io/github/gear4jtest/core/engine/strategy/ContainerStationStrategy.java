package io.github.gear4jtest.core.engine.strategy;

import java.util.Objects;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.ParallelExecutionConfiguration;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.spi.runner.StationRunner;

/**
 * Coordinates container outcomes while delegating ordering/concurrency
 * mechanics.
 */
public class ContainerStationStrategy extends AbstractStationStrategy<ContainerBaseStation<?, ?>> {
    private final SequentialContainerBranchExecutor sequentialExecutor;
    private final ParallelContainerBranchExecutor parallelExecutor;
    private final ParallelExecutionConfiguration parallelConfiguration;

    public ContainerStationStrategy() {
        this(ParallelExecutionConfiguration.defaults());
    }

    public ContainerStationStrategy(ParallelExecutionConfiguration parallelConfiguration) {
        this.parallelConfiguration = Objects.requireNonNull(parallelConfiguration,
                                                            "parallelConfiguration must not be null");
        this.sequentialExecutor = new SequentialContainerBranchExecutor();
        this.parallelExecutor = new ParallelContainerBranchExecutor();
    }

    @Override
    public boolean supports(Class<? extends AbstractStation<?, ?>> type) {
        return ContainerBaseStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(ContainerBaseStation<?, ?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext context) {
        ContainerBranchExecutionSupport.validateSiblingConditionsCompatibility(station);
        FlowConfig flowConfig = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());
        ContainerExecutionAggregation aggregation = station.isParallel() && station.getExecutorService() != null
                ? parallelExecutor.execute(station, input, runner, context, flowConfig,
                                           parallelConfiguration.effectiveAwaitTimeout(station.getAwaitTimeout()))
                : sequentialExecutor.execute(station, input, runner, context, flowConfig);

        if (aggregation.interruptingChild().isPresent()) {
            var interruptingChild = aggregation.interruptingChild().orElseThrow();
            FlowStrategySupport.applyInterruptToParentLog(EngineStationContexts.trace(context),
                                                          interruptingChild,
                                                          flowConfig);
            return null;
        }
        if (!aggregation.collectedErrors().isEmpty()) {
            Throwable first = aggregation.collectedErrors().get(0);
            EngineStationContexts.trace(context).markFailed(first instanceof Exception exception ? exception
                    : new RuntimeException(first.getMessage(), first));
        }
        return ContainerBranchExecutionSupport.assembleReturnValue(station, aggregation.results());
    }
}
