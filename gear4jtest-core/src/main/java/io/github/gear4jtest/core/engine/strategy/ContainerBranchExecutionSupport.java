package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.github.gear4jtest.core.api.behavior.SiblingBranchOutcomes;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;

/**
 * Shared branch mechanics intentionally kept separate from execution ordering.
 */
final class ContainerBranchExecutionSupport {
    private ContainerBranchExecutionSupport() {
    }

    static Object clonePayload(Object input, StationExecutionContext context) {
        return context.getSupport().getPayloadCloner().clonePayload(input);
    }

    static boolean isBranchEligible(ContainerBaseStation.Branch<?> branch,
                                    Object input,
                                    StationExecutionContext context,
                                    Map<String, StationLogStatus> siblingStatuses) {
        return isBranchConditionSatisfied(branch, input, context)
                && isSiblingBranchConditionSatisfied(branch, input, context, siblingStatuses);
    }

    static boolean isBranchConditionSatisfied(ContainerBaseStation.Branch<?> branch,
                                              Object input,
                                              StationExecutionContext context) {
        return branch.getCondition() == null || evaluateBranchCondition(branch, input, context);
    }

    @SuppressWarnings("unchecked")
    private static <I> boolean evaluateBranchCondition(ContainerBaseStation.Branch<I> branch,
                                                       Object input,
                                                       StationExecutionContext context) {
        return branch.getCondition().test((I) input, context.getGlobalContext());
    }

    static boolean isSiblingBranchConditionSatisfied(ContainerBaseStation.Branch<?> branch,
                                                     Object input,
                                                     StationExecutionContext context,
                                                     Map<String, StationLogStatus> siblingStatuses) {
        return branch.getSiblingCondition() == null
                || evaluateSiblingBranchCondition(branch, input, context, siblingStatuses);
    }

    @SuppressWarnings("unchecked")
    private static <I> boolean evaluateSiblingBranchCondition(ContainerBaseStation.Branch<I> branch,
                                                              Object input,
                                                              StationExecutionContext context,
                                                              Map<String, StationLogStatus> siblingStatuses) {
        return branch.getSiblingCondition().test((I) input, context.getGlobalContext(),
                                                 SiblingBranchOutcomes.of(siblingStatuses));
    }

    static void validateSiblingConditionsCompatibility(ContainerBaseStation<?, ?> station) {
        if (!station.isParallel()) {
            return;
        }
        for (ContainerBaseStation.Branch<?> branch : station.getPipelines()) {
            if (branch.getSiblingCondition() != null) {
                throw new IllegalArgumentException(
                        "Sibling branch conditions are only supported in sequential containers");
            }
        }
    }

    static StationLogTrace conditionSkippedLog(ContainerBaseStation.Branch<?> branch,
                                               StationExecutionContext context) {
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markSkipped();
        log.setOutput(null);
        return log;
    }

    static StationLogTrace timeoutCancelledLog(ContainerBaseStation.Branch<?> branch,
                                               StationExecutionContext context,
                                               Duration awaitTimeout) {
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markCancelled(new TimeoutException("Container branch timed out after " + awaitTimeout));
        log.setOutput(null);
        return log;
    }

    static StationLogTrace cooperativeCancellationLog(ContainerBaseStation.Branch<?> branch,
                                                      StationExecutionContext context) {
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markCancelled(context.getGlobalContext().getCancellationToken().cancellationCause()
                .orElseThrow());
        log.setOutput(null);
        return log;
    }

    static StationLogTrace siblingInterruptedCancellationLog(ContainerBaseStation.Branch<?> branch,
                                                             StationExecutionContext context,
                                                             StationLogTrace interruptingChild) {
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markCancelled(new RuntimeException("Container branch cancelled because sibling branch interrupted flow: "
                + interruptingChild.getOperationId() + " [" + interruptingChild.getStatus() + "]"));
        log.setOutput(null);
        return log;
    }

    static StationLogTrace unexpectedFailureLog(ContainerBaseStation.Branch<?> branch,
                                                StationExecutionContext context,
                                                Throwable cause) {
        StationLogTrace log = newSyntheticChildLog(branch, context);
        Exception representative = cause instanceof Exception exception ? exception
                : new RuntimeException(cause != null ? cause.getMessage() : "Unknown branch failure", cause);
        log.markFailed(representative);
        log.setOutput(null);
        return log;
    }

    static StationLogTrace normalizeCompletedLog(ContainerBaseStation.Branch<?> branch,
                                                 StationLogTrace childLog,
                                                 StationExecutionContext context) {
        if (childLog == null) {
            return unexpectedFailureLog(branch, context,
                                        new IllegalStateException("Parallel branch returned null StationLogTrace"));
        }
        childLog.setParentOperationId(context.getRecord().getId());
        if (childLog.getBranchId() == null) {
            childLog.setBranchId(branch.getEffectiveId());
        }
        return childLog;
    }

    static List<StationLogTrace> asOrderedList(List<? extends ContainerBaseStation.Branch<?>> branches,
                                               StationLogTrace[] orderedResults,
                                               StationExecutionContext context) {
        List<StationLogTrace> results = new ArrayList<>(orderedResults.length);
        for (int index = 0; index < orderedResults.length; index++) {
            StationLogTrace log = orderedResults[index];
            if (log == null) {
                log = unexpectedFailureLog(branches.get(index), context,
                                           new IllegalStateException(
                                                   "Missing container branch result at index " + index));
            }
            results.add(log);
        }
        return results;
    }

    static Object assembleReturnValue(ContainerBaseStation<?, ?> station, List<StationLogTrace> executions) {
        Object[] returnedObjects = executions.stream().map(StationLogTrace::getOutput).toArray();
        return station.getFunc() != null ? station.getFunc().apply(returnedObjects) : null;
    }

    private static StationLogTrace newSyntheticChildLog(ContainerBaseStation.Branch<?> branch,
                                                        StationExecutionContext context) {
        StationLogTrace log = StationLogTrace.start(context.getGlobalContext().getExecutionId(),
                                                    branch.getStation().getId(), context.getRecord().getId());
        log.setItemId(context.getGlobalContext().getCurrentItemId());
        log.setBranchId(branch.getEffectiveId());
        log.setContext(new HashMap<>());
        return log;
    }
}
