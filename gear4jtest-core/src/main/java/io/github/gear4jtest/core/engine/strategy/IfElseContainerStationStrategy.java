package io.github.gear4jtest.core.engine.strategy;

import java.util.List;

import io.github.gear4jtest.core.api.config.CancelPolicy;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.StopPolicy;
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object doExecute(
            UnaryIfElseContainerStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution) {

        StationLog selectedBranchLog = null;

        for (ContainerBaseStation.Branch element : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
            if (element.getCondition() == null
                    || element.getCondition().test(input, operationExecution.getGlobalContext())) {
                Object newObject = deepClone(input);
                selectedBranchLog = runner.run(newObject, element.getStation(), operationExecution);
                selectedBranchLog.setParentOperationId(operationExecution.getRecord().getId());
                break;
            }
        }

        if (selectedBranchLog == null && station.getElseOp() != null) {
            Object newObject = deepClone(input);
            selectedBranchLog = runner.run(newObject, station.getElseOp(), operationExecution);
            selectedBranchLog.setParentOperationId(operationExecution.getRecord().getId());
        }

        if (selectedBranchLog == null) {
            return null;
        }

        if (selectedBranchLog.getStatus() == StationLog.Status.SUCCEEDED
                || selectedBranchLog.getStatus() == StationLog.Status.SKIPPED) {
            return selectedBranchLog.getOutput();
        }

        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());
        FlowStrategySupport.applyInterruptToParentLog(operationExecution.getRecord(), selectedBranchLog, config);
        return null;
    }

    private static void applyInterruptToParentLog(
            StationLog parent,
            StationLog child,
            FlowConfig config) {

        StationLog.Status childStatus = child.getStatus();

        Exception representative = null;
        if (child.getThrowables() != null && !child.getThrowables().isEmpty()) {
            Throwable t = child.getThrowables().get(0);
            representative = (t instanceof Exception ex)
                    ? ex
                    : new RuntimeException(t.getMessage(), t);
        } else if (child.getErrorMessage() != null) {
            representative = new RuntimeException(child.getErrorMessage());
        }

        if (childStatus == StationLog.Status.FAILED) {
            parent.markFailed(representative);
            return;
        }

        if (childStatus == StationLog.Status.STOPPED) {
            if (config.stopPolicy() == StopPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markStopped(representative);
            }
            return;
        }

        if (childStatus == StationLog.Status.CANCELLED) {
            if (config.cancelPolicy() == CancelPolicy.TREAT_AS_FAILURE) {
                parent.markFailed(representative);
            } else {
                parent.markCancelled(representative);
            }
            return;
        }

        parent.markFailed(representative != null
                ? representative
                : new RuntimeException("Unknown terminal status: " + childStatus));
    }

    <T> T deepClone(T object) {
        return object;
    }
}
