package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.model.StationLogStatus;

import java.util.List;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class IfElseContainerStationStrategy extends AbstractStationStrategy<UnaryIfElseContainerStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return UnaryIfElseContainerStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(
            UnaryIfElseContainerStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution) {

        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());
        StationLogTrace selectedBranchLog = null;

        for (ContainerBaseStation.Branch element : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
            if (element.getCondition() == null
                    || element.getCondition().test(input, operationExecution.getGlobalContext())) {
                Object newObject = clonePayload(input, operationExecution);
                selectedBranchLog = runner.run(newObject, element.getStation(), operationExecution);
                selectedBranchLog.setParentOperationId(operationExecution.getRecord().getId());
                break;
            }
        }

        if (selectedBranchLog == null && station.getElseOp() != null) {
            Object newObject = clonePayload(input, operationExecution);
            selectedBranchLog = runner.run(newObject, station.getElseOp(), operationExecution);
            selectedBranchLog.setParentOperationId(operationExecution.getRecord().getId());
        }

        if (selectedBranchLog == null) {
            return null;
        }

        if (selectedBranchLog.getStatus() == StationLogStatus.SUCCEEDED
                || selectedBranchLog.getStatus() == StationLogStatus.SKIPPED) {
            return selectedBranchLog.getOutput();
        }

        FlowStrategySupport.applyInterruptToParentLog(operationExecution.getRecord(), selectedBranchLog, config);
        return null;
    }
}
