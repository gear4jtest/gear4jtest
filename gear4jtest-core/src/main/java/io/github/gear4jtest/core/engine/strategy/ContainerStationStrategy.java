package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.gear4jtest.core.api.behavior.SiblingBranchOutcomes;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.persistence.StationLog;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class ContainerStationStrategy extends AbstractStationStrategy<ContainerBaseStation> {

    @Override
    public boolean supports(Class<? extends AbstractStation> type) {
        return ContainerBaseStation.class.isAssignableFrom(type);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Object doExecute(
            ContainerBaseStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution) {

        validateSiblingConditionsCompatibility(station);

        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());

        ExecutionAggregation aggregation = station.isParallel() && station.getExecutorService() != null
                ? executeParallelBranches(station, input, runner, operationExecution, config)
                : executeSequentialBranches(station, input, runner, operationExecution, config);

        if (aggregation.interruptingChild != null) {
            FlowStrategySupport.applyInterruptToParentLog(
                    operationExecution.getRecord(),
                    aggregation.interruptingChild,
                    config);
            return null;
        }

        if (!aggregation.collectedErrors.isEmpty()) {
            Throwable first = aggregation.collectedErrors.get(0);
            operationExecution.getRecord().markFailed(
                    first instanceof Exception ex ? ex : new RuntimeException(first.getMessage(), first));
        }

        return returns(station, aggregation.results);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ExecutionAggregation executeSequentialBranches(
            ContainerBaseStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution,
            FlowConfig config) {

        List<StationLog> results = new ArrayList<>();
        List<Throwable> collectedErrors = new ArrayList<>();
        Map<String, StationLog.Status> siblingStatuses = new LinkedHashMap<>();

        for (ContainerBaseStation.Branch branch : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
            StationLog childLog;

            if (!isBranchEligible(branch, input, operationExecution, siblingStatuses)) {
                childLog = buildConditionSkippedBranchLog(branch, operationExecution);
            } else {
                Object newObject = clonePayload(input, operationExecution);
                childLog = runner.run(newObject, branch.getStation(), operationExecution);
            }

            results.add(childLog);
            siblingStatuses.put(branch.getEffectiveId(), childLog.getStatus());

            FlowDecision decision = FlowDecider.decide(childLog, config);
            switch (decision) {
                case PROCEED -> {
                    // no-op
                }
                case MARK_AND_PROCEED -> collectedErrors.add(
                        FlowStrategySupport.representativeThrowable(
                                childLog,
                                "Container branch failed without exception: " + childLog.getOperationId()));
                case INTERRUPT -> {
                    return new ExecutionAggregation(results, collectedErrors, childLog);
                }
            }
        }

        return new ExecutionAggregation(results, collectedErrors, null);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ExecutionAggregation executeParallelBranches(
            ContainerBaseStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution,
            FlowConfig config) {

        List<ContainerBaseStation.Branch> branches = (List<ContainerBaseStation.Branch>) station.getPipelines();
        StationLog[] orderedResults = new StationLog[branches.size()];
        List<Throwable> collectedErrors = new ArrayList<>();

        String currentItemId = operationExecution.getGlobalContext().getCurrentItemId();
        ExecutorService executor = operationExecution.getSupport()
                .executorFor(station.getExecutorService(), operationExecution.getGlobalContext());

        CompletionService<BranchExecution> completionService = new ExecutorCompletionService<>(executor);
        List<SubmittedBranch> submittedBranches = new ArrayList<>();
        Map<Future<BranchExecution>, SubmittedBranch> submittedByFuture = new IdentityHashMap<>();

        for (int index = 0; index < branches.size(); index++) {
            ContainerBaseStation.Branch branch = branches.get(index);

            if (!isBranchConditionSatisfied(branch, input, operationExecution)) {
                orderedResults[index] = buildConditionSkippedBranchLog(branch, operationExecution);
                continue;
            }

            Callable<StationLog> task = operationExecution.getSupport()
                    .getTaskFactory()
                    .createTask(() -> clonePayload(input, operationExecution), branch.getStation(), runner, operationExecution, currentItemId);

            int finalIndex = index;
            Future<BranchExecution> future =
                    completionService.submit(() -> new BranchExecution(finalIndex, branch, task.call()));

            SubmittedBranch submitted = new SubmittedBranch(index, branch, future);
            submittedBranches.add(submitted);
            submittedByFuture.put(future, submitted);
        }

        if (submittedBranches.isEmpty()) {
            return new ExecutionAggregation(asOrderedList(branches, orderedResults, operationExecution), collectedErrors, null);
        }

        long deadlineNanos = computeDeadlineNanos(station.getAwaitTimeout());
        int completedCount = 0;

        try {
            while (completedCount < submittedBranches.size()) {
                Future<BranchExecution> completedFuture =
                        waitForNextCompletion(completionService, deadlineNanos, station.getAwaitTimeout());

                if (completedFuture == null) {
                    StationLog timeoutChild = timeoutPendingBranches(
                            submittedBranches,
                            orderedResults,
                            operationExecution,
                            station.getAwaitTimeout());

                    if (timeoutChild != null
                            && FlowDecider.decide(timeoutChild, config) == FlowDecision.INTERRUPT) {
                        return new ExecutionAggregation(
                                asOrderedList(branches, orderedResults, operationExecution),
                                collectedErrors,
                                timeoutChild);
                    }

                    return new ExecutionAggregation(
                            asOrderedList(branches, orderedResults, operationExecution),
                            collectedErrors,
                            null);
                }

                SubmittedBranch submitted = submittedByFuture.get(completedFuture);
                BranchExecution execution = readCompletedExecution(completedFuture, submitted, operationExecution);
                completedCount++;

                StationLog childLog = normalizeCompletedLog(execution.branch, execution.log, operationExecution);
                orderedResults[execution.index] = childLog;

                FlowDecision decision = FlowDecider.decide(childLog, config);
                switch (decision) {
                    case PROCEED -> {
                        // no-op
                    }
                    case MARK_AND_PROCEED -> collectedErrors.add(
                            FlowStrategySupport.representativeThrowable(
                                    childLog,
                                    "Container branch failed without exception: " + childLog.getOperationId()));
                    case INTERRUPT -> {
                        cancelPendingBranchesAfterInterrupt(
                                submittedBranches,
                                orderedResults,
                                operationExecution,
                                childLog);
                        return new ExecutionAggregation(
                                asOrderedList(branches, orderedResults, operationExecution),
                                collectedErrors,
                                childLog);
                    }
                }
            }

            return new ExecutionAggregation(
                    asOrderedList(branches, orderedResults, operationExecution),
                    collectedErrors,
                    null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelPendingBranchesAfterUnexpectedInterruption(submittedBranches, orderedResults, operationExecution);
            throw new RuntimeException("Interrupted while waiting for container branches", e);
        }
    }

    private void validateSiblingConditionsCompatibility(ContainerBaseStation station) {
        if (!station.isParallel()) {
            return;
        }

        for (Object rawBranch : station.getPipelines()) {
            ContainerBaseStation.Branch<?> branch = (ContainerBaseStation.Branch<?>) rawBranch;
            if (branch.getSiblingCondition() != null) {
                throw new IllegalArgumentException(
                        "Sibling branch conditions are only supported in sequential containers");
            }
        }
    }

    private long computeDeadlineNanos(Duration awaitTimeout) {
        if (awaitTimeout == null) {
            return Long.MAX_VALUE;
        }
        return System.nanoTime() + awaitTimeout.toNanos();
    }

    private Future<BranchExecution> waitForNextCompletion(
            CompletionService<BranchExecution> completionService,
            long deadlineNanos,
            Duration awaitTimeout) throws InterruptedException {

        if (awaitTimeout == null) {
            return completionService.take();
        }

        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return null;
        }

        return completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private BranchExecution readCompletedExecution(
            Future<BranchExecution> completedFuture,
            SubmittedBranch submitted,
            StationExecutionContext operationExecution) {

        try {
            return completedFuture.get();
        } catch (CancellationException e) {
            StationLog cancelled = buildUnexpectedFailureBranchLog(
                    submitted.branch,
                    operationExecution,
                    new RuntimeException("Completed branch future was cancelled unexpectedly", e));
            return new BranchExecution(submitted.index, submitted.branch, cancelled);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }

            StationLog failure = buildUnexpectedFailureBranchLog(submitted.branch, operationExecution, cause);
            return new BranchExecution(submitted.index, submitted.branch, failure);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while reading a completed branch", e);
        }
    }

    private StationLog timeoutPendingBranches(
            List<SubmittedBranch> submittedBranches,
            StationLog[] orderedResults,
            StationExecutionContext operationExecution,
            Duration awaitTimeout) {

        StationLog firstTimeoutLog = null;

        for (SubmittedBranch submitted : submittedBranches) {
            if (orderedResults[submitted.index] != null) {
                continue;
            }

            StationLog completedLog = tryResolveAlreadyCompletedBranch(submitted, operationExecution);
            if (completedLog != null) {
                orderedResults[submitted.index] = completedLog;
                continue;
            }

            submitted.future.cancel(true);
            StationLog timeoutLog = buildTimeoutCancelledBranchLog(submitted.branch, operationExecution, awaitTimeout);
            orderedResults[submitted.index] = timeoutLog;

            if (firstTimeoutLog == null) {
                firstTimeoutLog = timeoutLog;
            }
        }

        return firstTimeoutLog;
    }

    private void cancelPendingBranchesAfterInterrupt(
            List<SubmittedBranch> submittedBranches,
            StationLog[] orderedResults,
            StationExecutionContext operationExecution,
            StationLog interruptingChild) {

        for (SubmittedBranch submitted : submittedBranches) {
            if (orderedResults[submitted.index] != null) {
                continue;
            }

            StationLog completedLog = tryResolveAlreadyCompletedBranch(submitted, operationExecution);
            if (completedLog != null) {
                orderedResults[submitted.index] = completedLog;
                continue;
            }

            submitted.future.cancel(true);
            orderedResults[submitted.index] = buildInterruptedCancelledBranchLog(
                    submitted.branch,
                    operationExecution,
                    interruptingChild);
        }
    }

    private void cancelPendingBranchesAfterUnexpectedInterruption(
            List<SubmittedBranch> submittedBranches,
            StationLog[] orderedResults,
            StationExecutionContext operationExecution) {

        for (SubmittedBranch submitted : submittedBranches) {
            if (orderedResults[submitted.index] != null) {
                continue;
            }

            StationLog completedLog = tryResolveAlreadyCompletedBranch(submitted, operationExecution);
            if (completedLog != null) {
                orderedResults[submitted.index] = completedLog;
                continue;
            }

            submitted.future.cancel(true);
            orderedResults[submitted.index] = buildUnexpectedFailureBranchLog(
                    submitted.branch,
                    operationExecution,
                    new RuntimeException("Branch cancelled because the container wait was interrupted"));
        }
    }

    private StationLog tryResolveAlreadyCompletedBranch(
            SubmittedBranch submitted,
            StationExecutionContext operationExecution) {

        if (!submitted.future.isDone() || submitted.future.isCancelled()) {
            return null;
        }

        try {
            BranchExecution execution = submitted.future.get();
            return normalizeCompletedLog(execution.branch, execution.log, operationExecution);
        } catch (CancellationException e) {
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            return buildUnexpectedFailureBranchLog(submitted.branch, operationExecution, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while harvesting a completed branch", e);
        }
    }

    private StationLog normalizeCompletedLog(
            ContainerBaseStation.Branch<?> branch,
            StationLog childLog,
            StationExecutionContext operationExecution) {

        if (childLog == null) {
            return buildUnexpectedFailureBranchLog(
                    branch,
                    operationExecution,
                    new IllegalStateException("Parallel branch returned null StationLog"));
        }

        childLog.setParentOperationId(operationExecution.getRecord().getId());
        return childLog;
    }

    private List<StationLog> asOrderedList(
            List<ContainerBaseStation.Branch> branches,
            StationLog[] orderedResults,
            StationExecutionContext operationExecution) {

        List<StationLog> results = new ArrayList<>(orderedResults.length);

        for (int index = 0; index < orderedResults.length; index++) {
            StationLog log = orderedResults[index];
            if (log == null) {
                log = buildUnexpectedFailureBranchLog(
                        branches.get(index),
                        operationExecution,
                        new IllegalStateException("Missing container branch result at index " + index));
            }
            results.add(log);
        }

        return results;
    }

    private Object returns(ContainerBaseStation station, List<StationLog> executions) {
        Object[] returnedObjects = executions.stream()
                .map(StationLog::getOutput)
                .toArray();

        if (station.getFunc() != null) {
            return station.getFunc().apply(returnedObjects);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean isBranchEligible(
            ContainerBaseStation.Branch branch,
            Object input,
            StationExecutionContext operationExecution,
            Map<String, StationLog.Status> siblingStatuses) {

        if (!isBranchConditionSatisfied(branch, input, operationExecution)) {
            return false;
        }

        return isSiblingBranchConditionSatisfied(branch, input, operationExecution, siblingStatuses);
    }

    @SuppressWarnings("unchecked")
    private boolean isBranchConditionSatisfied(
            ContainerBaseStation.Branch branch,
            Object input,
            StationExecutionContext operationExecution) {

        if (branch.getCondition() == null) {
            return true;
        }

        return branch.getCondition().test(input, operationExecution.getGlobalContext());
    }

    @SuppressWarnings("unchecked")
    private boolean isSiblingBranchConditionSatisfied(
            ContainerBaseStation.Branch branch,
            Object input,
            StationExecutionContext operationExecution,
            Map<String, StationLog.Status> siblingStatuses) {

        if (branch.getSiblingCondition() == null) {
            return true;
        }

        return branch.getSiblingCondition().test(
                input,
                operationExecution.getGlobalContext(),
                SiblingBranchOutcomes.of(siblingStatuses));
    }

    private StationLog buildConditionSkippedBranchLog(
            ContainerBaseStation.Branch<?> branch,
            StationExecutionContext operationExecution) {

        StationLog log = newSyntheticChildLog(branch, operationExecution);
        log.markSkipped();
        log.setOutput(null);
        return log;
    }

    private StationLog buildTimeoutCancelledBranchLog(
            ContainerBaseStation.Branch<?> branch,
            StationExecutionContext operationExecution,
            Duration awaitTimeout) {

        StationLog log = newSyntheticChildLog(branch, operationExecution);
        log.markCancelled(new TimeoutException("Container branch timed out after " + awaitTimeout));
        log.setOutput(null);
        return log;
    }

    private StationLog buildInterruptedCancelledBranchLog(
            ContainerBaseStation.Branch<?> branch,
            StationExecutionContext operationExecution,
            StationLog interruptingChild) {

        StationLog log = newSyntheticChildLog(branch, operationExecution);
        log.markCancelled(new RuntimeException(
                "Container branch cancelled because sibling branch interrupted flow: "
                        + interruptingChild.getOperationId()
                        + " [" + interruptingChild.getStatus() + "]"));
        log.setOutput(null);
        return log;
    }

    private StationLog buildUnexpectedFailureBranchLog(
            ContainerBaseStation.Branch<?> branch,
            StationExecutionContext operationExecution,
            Throwable cause) {

        StationLog log = newSyntheticChildLog(branch, operationExecution);

        Exception representative = cause instanceof Exception ex
                ? ex
                : new RuntimeException(cause != null ? cause.getMessage() : "Unknown branch failure", cause);

        log.markFailed(representative);
        log.setOutput(null);
        return log;
    }

    private StationLog newSyntheticChildLog(
            ContainerBaseStation.Branch<?> branch,
            StationExecutionContext operationExecution) {

        StationLog log = StationLog.start(
                operationExecution.getGlobalContext().getExecutionId(),
                branch.getStation().getId(),
                operationExecution.getRecord().getId());

        log.setItemId(operationExecution.getGlobalContext().getCurrentItemId());
        log.setContext(new HashMap<>());
        return log;
    }

    private static final class ExecutionAggregation {
        private final List<StationLog> results;
        private final List<Throwable> collectedErrors;
        private final StationLog interruptingChild;

        private ExecutionAggregation(
                List<StationLog> results,
                List<Throwable> collectedErrors,
                StationLog interruptingChild) {
            this.results = results;
            this.collectedErrors = collectedErrors;
            this.interruptingChild = interruptingChild;
        }
    }

    private static final class SubmittedBranch {
        private final int index;
        private final ContainerBaseStation.Branch<?> branch;
        private final Future<BranchExecution> future;

        private SubmittedBranch(int index, ContainerBaseStation.Branch<?> branch, Future<BranchExecution> future) {
            this.index = index;
            this.branch = branch;
            this.future = future;
        }
    }

    private static final class BranchExecution {
        private final int index;
        private final ContainerBaseStation.Branch<?> branch;
        private final StationLog log;

        private BranchExecution(int index, ContainerBaseStation.Branch<?> branch, StationLog log) {
            this.index = index;
            this.branch = branch;
            this.log = log;
        }
    }
}
