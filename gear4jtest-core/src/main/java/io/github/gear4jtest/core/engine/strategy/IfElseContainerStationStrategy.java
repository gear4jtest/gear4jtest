package io.github.gear4jtest.core.engine.strategy;

import java.util.List;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class IfElseContainerStationStrategy extends AbstractStationStrategy<UnaryIfElseContainerStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return UnaryIfElseContainerStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(UnaryIfElseContainerStation station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());
        StationLog selectedBranchLog = null;

        for (ContainerBaseStation.Branch element : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
            if (element.getCondition() == null
                    || element.getCondition().test(input, operationExecution.getGlobalContext())) {
                selectedBranchLog = runner.run(input, element.getStation(), operationExecution);
                break;
            }
        }

        if (selectedBranchLog == null && station.getElseOp() != null) {
            selectedBranchLog = runner.run(input, station.getElseOp(), operationExecution);
        }

        if (selectedBranchLog == null) {
            return null;
        }

        if (selectedBranchLog.getStatus() == StationLog.Status.SUCCEEDED
                || selectedBranchLog.getStatus() == StationLog.Status.SKIPPED) {
            return selectedBranchLog.getOutput();
        }

        FlowStrategySupport.applyInterruptToParentLog(operationExecution.getRecord(), selectedBranchLog, config);
        return null;
    }
}
