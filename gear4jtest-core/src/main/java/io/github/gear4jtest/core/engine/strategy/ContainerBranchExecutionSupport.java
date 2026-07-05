package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import io.github.gear4jtest.core.api.behavior.SiblingBranchOutcomes;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.ContainerResults;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.model.StationLogStatus;

/**
 * Shared branch mechanics intentionally kept separate from execution ordering.
 */
final class ContainerBranchExecutionSupport {
    private ContainerBranchExecutionSupport() {
    }

    static Object clonePayload(Object input, StationExecutionContext context) {
        return EngineStationContexts.support(context).getPayloadCloner().clonePayload(input);
    }

    static boolean isBranchEligible(ContainerBaseStation.Branch<?> branch,
                                    Object input,
                                    StationExecutionContext context,
                                    Map<String, StationLogStatus> siblingStatuses) {
        return skipReason(branch, input, context, siblingStatuses) == null;
    }

    static StationSkipReason skipReason(ContainerBaseStation.Branch<?> branch,
                                        Object input,
                                        StationExecutionContext context,
                                        Map<String, StationLogStatus> siblingStatuses) {
        if (!isBranchConditionSatisfied(branch, input, context)) {
            return StationSkipReason.CONDITION_NOT_SATISFIED;
        }
        if (!isSiblingBranchConditionSatisfied(branch, input, context, siblingStatuses)) {
            return StationSkipReason.SIBLING_CONDITION_NOT_SATISFIED;
        }
        return null;
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
        for (ContainerBaseStation.Branch<?> branch : station.getAssemblyLines()) {
            if (branch.getSiblingCondition() != null) {
                throw new IllegalArgumentException(
                        "Sibling branch conditions are only supported in sequential containers");
            }
        }
    }

    static StationLogTrace conditionSkippedLog(ContainerBaseStation.Branch<?> branch,
                                               Object input,
                                               StationExecutionContext context,
                                               StationSkipReason reason) {
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markSkipped();
        log.setOutput(null);
        log.getContext().put("synthetic.reason", reason.name());
        return EngineStationContexts.support(context).getSyntheticStationLifecycleRecorder()
                .recordSkipped(context, branch.getStation(), log, input, reason);
    }

    static StationLogTrace timeoutCancelledLog(ContainerBaseStation.Branch<?> branch,
                                               Object input,
                                               StationExecutionContext context,
                                               Duration awaitTimeout) {
        TimeoutException timeout = new TimeoutException("Container branch timed out after " + awaitTimeout);
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markCancelled(timeout);
        log.setOutput(null);
        log.getContext().put("synthetic.reason", StationCancellationReason.TIMEOUT.name());
        return EngineStationContexts.support(context).getSyntheticStationLifecycleRecorder()
                .recordCancelled(context, branch.getStation(), log, input, StationCancellationReason.TIMEOUT, timeout);
    }

    static StationLogTrace cooperativeCancellationLog(ContainerBaseStation.Branch<?> branch,
                                                      Object input,
                                                      StationExecutionContext context) {
        Exception cause = context.getGlobalContext().getCancellationToken().cancellationCause().orElseThrow();
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markCancelled(cause);
        log.setOutput(null);
        log.getContext().put("synthetic.reason", StationCancellationReason.COOPERATIVE_CANCELLATION.name());
        return EngineStationContexts.support(context).getSyntheticStationLifecycleRecorder()
                .recordCancelled(context, branch.getStation(), log, input,
                                 StationCancellationReason.COOPERATIVE_CANCELLATION, cause);
    }

    static StationLogTrace siblingInterruptedCancellationLog(ContainerBaseStation.Branch<?> branch,
                                                             Object input,
                                                             StationExecutionContext context,
                                                             StationLogTrace interruptingChild) {
        RuntimeException cause = new RuntimeException(
                "Container branch cancelled because sibling branch interrupted flow: "
                        + interruptingChild.getOperationId() + " [" + interruptingChild.getStatus() + "]");
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markCancelled(cause);
        log.setOutput(null);
        log.getContext().put("synthetic.reason", StationInterruptionReason.SIBLING_FLOW_INTERRUPTED.name());
        return EngineStationContexts.support(context).getSyntheticStationLifecycleRecorder()
                .recordInterrupted(context, branch.getStation(), log, input,
                                   StationInterruptionReason.SIBLING_FLOW_INTERRUPTED,
                                   interruptingChild.getOperationId(), cause);
    }

    static StationLogTrace waitInterruptedCancellationLog(ContainerBaseStation.Branch<?> branch,
                                                          Object input,
                                                          StationExecutionContext context) {
        RuntimeException cause = new RuntimeException(
                "Branch cancelled because the container wait was interrupted");
        StationLogTrace log = newSyntheticChildLog(branch, context);
        log.markCancelled(cause);
        log.setOutput(null);
        log.getContext().put("synthetic.reason", StationCancellationReason.UNEXPECTED_WAIT_INTERRUPTION.name());
        return EngineStationContexts.support(context).getSyntheticStationLifecycleRecorder()
                .recordCancelled(context, branch.getStation(), log, input,
                                 StationCancellationReason.UNEXPECTED_WAIT_INTERRUPTION, cause);
    }

    static StationLogTrace unexpectedFailureLog(ContainerBaseStation.Branch<?> branch,
                                                Object input,
                                                StationExecutionContext context,
                                                Throwable cause) {
        StationLogTrace log = newSyntheticChildLog(branch, context);
        Exception representative = cause instanceof Exception exception ? exception
                : new RuntimeException(cause != null ? cause.getMessage() : "Unknown branch failure", cause);
        log.markFailed(representative);
        log.setOutput(null);
        log.getContext().put("synthetic.reason", "FAILED_BEFORE_START");
        return EngineStationContexts.support(context).getSyntheticStationLifecycleRecorder()
                .recordFailedBeforeStart(context, branch.getStation(), log, input, representative);
    }

    static StationLogTrace normalizeCompletedLog(ContainerBaseStation.Branch<?> branch,
                                                 StationLogTrace childLog,
                                                 Object input,
                                                 StationExecutionContext context) {
        if (childLog == null) {
            return unexpectedFailureLog(branch, input, context,
                                        new IllegalStateException("Parallel branch returned null StationLogTrace"));
        }
        childLog.setParentOperationId(context.getRecord().getId());
        if (childLog.getBranchId() == null) {
            childLog.setBranchId(branch.getId());
        }
        return childLog;
    }

    static List<StationLogTrace> asOrderedList(List<? extends ContainerBaseStation.Branch<?>> branches,
                                               StationLogTrace[] orderedResults,
                                               Object input,
                                               StationExecutionContext context) {
        List<StationLogTrace> results = new ArrayList<>(orderedResults.length);
        for (int index = 0; index < orderedResults.length; index++) {
            StationLogTrace log = orderedResults[index];
            if (log == null) {
                log = unexpectedFailureLog(branches.get(index), input, context,
                                           new IllegalStateException(
                                                   "Missing container branch result at index " + index));
            }
            results.add(log);
        }
        return results;
    }

    static Object assembleReturnValue(ContainerBaseStation<?, ?> station, List<StationLogTrace> executions) {
        if (station.getResultsFunc() != null) {
            return station.getResultsFunc().apply(namedResults(station, executions));
        }
        return null;
    }

    private static ContainerResults namedResults(ContainerBaseStation<?, ?> station, List<StationLogTrace> executions) {
        Map<String, Object> byBranchId = new LinkedHashMap<>();
        List<Object> orderedOutputs = new ArrayList<>(executions.size());
        List<? extends ContainerBaseStation.Branch<?>> branches = station.getAssemblyLines();
        for (int index = 0; index < executions.size(); index++) {
            Object output = executions.get(index).getOutput();
            byBranchId.put(branches.get(index).getId(), output);
            orderedOutputs.add(output);
        }
        return ContainerResults.of(byBranchId, orderedOutputs);
    }

    private static StationLogTrace newSyntheticChildLog(ContainerBaseStation.Branch<?> branch,
                                                        StationExecutionContext context) {
        StationLogTrace log = StationLogTrace.start(context.getGlobalContext().getExecutionId(),
                                                    branch.getStation().getId(), context.getRecord().getId());
        log.setItemId(context.getGlobalContext().getCurrentItemId());
        log.setBranchId(branch.getId());
        log.setContext(new HashMap<>());
        return log;
    }
}
