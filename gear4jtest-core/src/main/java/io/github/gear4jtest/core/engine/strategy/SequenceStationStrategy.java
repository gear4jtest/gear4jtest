package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.List;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class SequenceStationStrategy extends AbstractStationStrategy<SequenceStation<?, ?>> {

    @Override
    public boolean supports(Class<? extends AbstractStation<?, ?>> type) {
        return SequenceStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(SequenceStation<?, ?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());

        Object currentInput = input;
        List<Throwable> collectedErrors = new ArrayList<>();

        for (AbstractStation<?, ?> child : station.getSteps()) {
            StationLogTrace childLog = runner.run(currentInput, child, operationExecution);

            FlowDecision decision = FlowDecider.decide(childLog, config);
            switch (decision) {
                case PROCEED -> {
                    if (childLog.getStatus() == StationLogStatus.SUCCEEDED) {
                        currentInput = childLog.getOutput();
                    }
                }
                case MARK_AND_PROCEED -> collectedErrors.add(FlowStrategySupport
                        .representativeThrowable(childLog, "Step failed without exception: " + child.getId()));
                case INTERRUPT -> {
                    StationLogTrace parentLog = operationExecution.getRecord();
                    FlowStrategySupport.applyInterruptToParentLog(parentLog, childLog, config);
                    parentLog.setOutput(currentInput);
                    return currentInput;
                }
            }
        }

        if (!collectedErrors.isEmpty()) {
            StationLogTrace parentLog = operationExecution.getRecord();
            Throwable first = collectedErrors.get(0);
            if (first instanceof Exception ex) {
                parentLog.markFailed(ex);
            } else {
                parentLog.markFailed(new RuntimeException(first.getMessage(), first));
            }
            parentLog.setOutput(currentInput);
            return currentInput;
        }

        return currentInput;
    }
}
