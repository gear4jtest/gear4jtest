package io.github.gear4jtest.core.engine.strategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

/**
 * Executes container branches serially while preserving sibling-aware
 * conditions.
 */
final class SequentialContainerBranchExecutor {
    ContainerExecutionAggregation execute(ContainerBaseStation<?, ?> station,
                                          Object input,
                                          StationRunner runner,
                                          StationExecutionContext context,
                                          FlowConfig flowConfig) {
        List<StationLogTrace> results = new ArrayList<>();
        List<Throwable> collectedErrors = new ArrayList<>();
        Map<String, StationLogStatus> siblingStatuses = new LinkedHashMap<>();

        for (ContainerBaseStation.Branch<?> branch : station.getAssemblyLines()) {
            StationLogTrace childLog;
            StationSkipReason skipReason = ContainerBranchExecutionSupport.skipReason(branch, input, context,
                                                                                      siblingStatuses);
            if (skipReason != null) {
                childLog = ContainerBranchExecutionSupport.conditionSkippedLog(branch, input, context, skipReason);
            } else {
                Object branchInput = ContainerBranchExecutionSupport.clonePayload(input, context);
                try (var ignored = context.getGlobalContext().enterBranch(branch.getId())) {
                    childLog = runner.run(branchInput, branch.getStation(), context);
                }
            }

            results.add(childLog);
            siblingStatuses.put(branch.getId(), childLog.getStatus());
            FlowDecision decision = FlowDecider.decide(childLog, flowConfig);
            switch (decision) {
                case PROCEED -> {
                    // Continue with the next branch.
                }
                case MARK_AND_PROCEED -> collectedErrors.add(FlowStrategySupport.representativeThrowable(childLog,
                                                                                                         "Container branch failed without exception: "
                                                                                                                 + childLog
                                                                                                                         .getOperationId()));
                case INTERRUPT -> {
                    return new ContainerExecutionAggregation(results, collectedErrors, childLog);
                }
            }
        }
        return new ContainerExecutionAggregation(results, collectedErrors, null);
    }
}
