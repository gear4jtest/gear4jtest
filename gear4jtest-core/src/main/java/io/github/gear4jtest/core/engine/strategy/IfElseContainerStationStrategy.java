package io.github.gear4jtest.core.engine.strategy;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.UnaryIfElseContainerStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class IfElseContainerStationStrategy extends AbstractStationStrategy<UnaryIfElseContainerStation<?>> {
    @SuppressWarnings("unchecked")
    private static <I> boolean evaluateBranchCondition(ContainerBaseStation.Branch<I> branch,
                                                       Object input,
                                                       StationExecutionContext ctx) {
        return branch.getCondition().test((I) input, ctx.getGlobalContext());
    }

    @Override
    public boolean supports(Class<? extends AbstractStation<?, ?>> type) {
        return UnaryIfElseContainerStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(UnaryIfElseContainerStation<?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {

        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());
        StationLogTrace selectedBranchLog = null;
        String selectedBranchId = null;

        for (ContainerBaseStation.Branch<?> element : station.getAssemblyLines()) {
            if (element.getCondition() == null || evaluateBranchCondition(element, input, operationExecution)) {
                Object newObject = clonePayload(input, operationExecution);
                selectedBranchId = element.getId();
                try (var ignored = operationExecution.getGlobalContext().enterBranch(selectedBranchId)) {
                    selectedBranchLog = runner.run(newObject, element.getStation(), operationExecution);
                }
                break;
            }
        }

        if (selectedBranchLog == null && station.getElseOp() != null) {
            Object newObject = clonePayload(input, operationExecution);
            selectedBranchId = station.getElseBranchId();
            try (var ignored = operationExecution.getGlobalContext().enterBranch(selectedBranchId)) {
                selectedBranchLog = runner.run(newObject, station.getElseOp(), operationExecution);
            }
        }

        if (selectedBranchLog == null) {
            return input;
        }
        normalizeSelectedBranchLog(selectedBranchLog, selectedBranchId, operationExecution);

        FlowDecision decision = FlowDecider.decide(selectedBranchLog, config);
        return switch (decision) {
            case PROCEED -> proceedOutput(input, selectedBranchLog);
            case MARK_AND_PROCEED -> {
                Throwable representative = FlowStrategySupport.representativeThrowable(
                                                                                       selectedBranchLog,
                                                                                       "If/else branch failed without exception: "
                                                                                               + selectedBranchLog
                                                                                                       .getOperationId());
                operationExecution.getRecord().markFailed(representative instanceof Exception exception ? exception
                        : new RuntimeException(representative.getMessage(), representative));
                yield proceedOutput(input, selectedBranchLog);
            }
            case INTERRUPT -> {
                FlowStrategySupport.applyInterruptToParentLog(operationExecution.getRecord(), selectedBranchLog,
                                                              config);
                yield null;
            }
        };
    }

    private static void normalizeSelectedBranchLog(StationLogTrace selectedBranchLog,
                                                   String selectedBranchId,
                                                   StationExecutionContext operationExecution) {
        selectedBranchLog.setParentOperationId(operationExecution.getRecord().getId());
        if (selectedBranchLog.getBranchId() == null) {
            selectedBranchLog.setBranchId(selectedBranchId);
        }
    }

    private static Object proceedOutput(Object input, StationLogTrace selectedBranchLog) {
        if (selectedBranchLog.getStatus() == StationLogStatus.SUCCEEDED
                || selectedBranchLog.getStatus() == StationLogStatus.SKIPPED) {
            return selectedBranchLog.getOutput();
        }
        return input;
    }
}
