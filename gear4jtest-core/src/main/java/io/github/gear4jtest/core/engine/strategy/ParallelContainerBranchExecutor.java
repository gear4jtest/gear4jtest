package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.FlowDecider;
import io.github.gear4jtest.core.api.config.FlowDecision;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;

/**
 * Executes parallel container branches with bounded waiting and deterministic
 * ordering.
 */
final class ParallelContainerBranchExecutor {
    ContainerExecutionAggregation execute(ContainerBaseStation<?, ?> station,
                                          Object input,
                                          StationRunner runner,
                                          StationExecutionContext context,
                                          FlowConfig flowConfig,
                                          Duration awaitTimeout) {
        List<? extends ContainerBaseStation.Branch<?>> branches = station.getPipelines();
        StationLogTrace[] orderedResults = new StationLogTrace[branches.size()];
        List<Throwable> collectedErrors = new ArrayList<>();
        String currentItemId = context.getGlobalContext().getCurrentItemId();
        ExecutorService executor = context.getSupport().executorFor(station.getExecutorService(),
                                                                    context.getGlobalContext());
        CompletionService<BranchExecution> completionService = new ExecutorCompletionService<>(executor);
        List<SubmittedBranch> submittedBranches = new ArrayList<>();
        Map<Future<BranchExecution>, SubmittedBranch> submittedByFuture = new IdentityHashMap<>();

        for (int index = 0; index < branches.size(); index++) {
            ContainerBaseStation.Branch<?> branch = branches.get(index);
            if (!ContainerBranchExecutionSupport.isBranchConditionSatisfied(branch, input, context)) {
                orderedResults[index] = ContainerBranchExecutionSupport.conditionSkippedLog(branch, context);
                continue;
            }
            if (context.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                orderedResults[index] = ContainerBranchExecutionSupport.cooperativeCancellationLog(branch, context);
                cancelPendingForCancellation(submittedBranches, orderedResults, context);
                return new ContainerExecutionAggregation(
                        ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                        collectedErrors, orderedResults[index]);
            }

            Callable<StationLogTrace> task = context.getSupport().getTaskFactory()
                    .createTask(() -> ContainerBranchExecutionSupport.clonePayload(input, context),
                                branch.getStation(), runner, context, currentItemId, branch.getEffectiveId());
            int finalIndex = index;
            try {
                Future<BranchExecution> future = completionService
                        .submit(() -> new BranchExecution(finalIndex, branch, task.call()));
                SubmittedBranch submitted = new SubmittedBranch(index, branch, future);
                submittedBranches.add(submitted);
                submittedByFuture.put(future, submitted);
            } catch (RejectedExecutionException rejected) {
                StationLogTrace rejectedLog = ContainerBranchExecutionSupport.unexpectedFailureLog(branch, context,
                                                                                                   rejected);
                orderedResults[index] = rejectedLog;
                FlowDecision decision = FlowDecider.decide(rejectedLog, flowConfig);
                if (decision == FlowDecision.MARK_AND_PROCEED) {
                    collectedErrors.add(rejected);
                } else if (decision == FlowDecision.INTERRUPT) {
                    cancelPendingAfterInterrupt(submittedBranches, orderedResults, context, rejectedLog);
                    return new ContainerExecutionAggregation(
                            ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                            collectedErrors, rejectedLog);
                }
            }
        }

        if (submittedBranches.isEmpty()) {
            return new ContainerExecutionAggregation(
                    ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                    collectedErrors, null);
        }

        long deadlineNanos = System.nanoTime() + awaitTimeout.toNanos();
        int completedCount = 0;
        try {
            while (completedCount < submittedBranches.size()) {
                if (context.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                    StationLogTrace cancellation = cancelPendingForCancellation(submittedBranches, orderedResults,
                                                                                context);
                    return new ContainerExecutionAggregation(
                            ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                            collectedErrors, cancellation);
                }

                Future<BranchExecution> completedFuture = waitForNextCompletion(completionService, deadlineNanos);
                if (completedFuture == null) {
                    StationLogTrace timeoutChild = timeoutPendingBranches(submittedBranches, orderedResults, context,
                                                                          awaitTimeout);
                    if (timeoutChild != null
                            && FlowDecider.decide(timeoutChild, flowConfig) == FlowDecision.INTERRUPT) {
                        return new ContainerExecutionAggregation(
                                ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                                collectedErrors, timeoutChild);
                    }
                    return new ContainerExecutionAggregation(
                            ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                            collectedErrors, null);
                }

                SubmittedBranch submitted = submittedByFuture.get(completedFuture);
                BranchExecution execution = readCompletedExecution(completedFuture, submitted, context);
                completedCount++;
                StationLogTrace childLog = ContainerBranchExecutionSupport.normalizeCompletedLog(execution.branch,
                                                                                                 execution.log,
                                                                                                 context);
                orderedResults[execution.index] = childLog;
                FlowDecision decision = FlowDecider.decide(childLog, flowConfig);
                switch (decision) {
                    case PROCEED -> {
                        // Continue waiting.
                    }
                    case MARK_AND_PROCEED -> collectedErrors.add(FlowStrategySupport.representativeThrowable(childLog,
                                                                                                             "Container branch failed without exception: "
                                                                                                                     + childLog
                                                                                                                             .getOperationId()));
                    case INTERRUPT -> {
                        cancelPendingAfterInterrupt(submittedBranches, orderedResults, context, childLog);
                        return new ContainerExecutionAggregation(
                                ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                                collectedErrors, childLog);
                    }
                }
            }
            return new ContainerExecutionAggregation(
                    ContainerBranchExecutionSupport.asOrderedList(branches, orderedResults, context),
                    collectedErrors, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelPendingAfterUnexpectedInterruption(submittedBranches, orderedResults, context);
            throw new RuntimeException("Interrupted while waiting for container branches", e);
        }
    }

    private Future<BranchExecution> waitForNextCompletion(CompletionService<BranchExecution> completionService,
                                                          long deadlineNanos)
            throws InterruptedException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        return remainingNanos <= 0L ? null : completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private BranchExecution readCompletedExecution(Future<BranchExecution> future,
                                                   SubmittedBranch submitted,
                                                   StationExecutionContext context) {
        try {
            return future.get();
        } catch (CancellationException e) {
            return new BranchExecution(submitted.index, submitted.branch,
                    ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch, context,
                                                                         new RuntimeException(
                                                                                 "Completed branch future was cancelled unexpectedly",
                                                                                 e)));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            return new BranchExecution(submitted.index, submitted.branch,
                    ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch, context, cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while reading a completed branch", e);
        }
    }

    private StationLogTrace timeoutPendingBranches(List<SubmittedBranch> submissions,
                                                   StationLogTrace[] orderedResults,
                                                   StationExecutionContext context,
                                                   Duration awaitTimeout) {
        StationLogTrace firstTimeoutLog = null;
        for (SubmittedBranch submitted : submissions) {
            if (orderedResults[submitted.index] != null) {
                continue;
            }
            StationLogTrace completed = tryResolveAlreadyCompletedBranch(submitted, context);
            if (completed != null) {
                orderedResults[submitted.index] = completed;
                continue;
            }
            submitted.future.cancel(true);
            StationLogTrace timeout = ContainerBranchExecutionSupport.timeoutCancelledLog(submitted.branch, context,
                                                                                          awaitTimeout);
            orderedResults[submitted.index] = timeout;
            if (firstTimeoutLog == null) {
                firstTimeoutLog = timeout;
            }
        }
        return firstTimeoutLog;
    }

    private void cancelPendingAfterInterrupt(List<SubmittedBranch> submissions,
                                             StationLogTrace[] orderedResults,
                                             StationExecutionContext context,
                                             StationLogTrace interruptingChild) {
        for (SubmittedBranch submitted : submissions) {
            if (orderedResults[submitted.index] != null) {
                continue;
            }
            StationLogTrace completed = tryResolveAlreadyCompletedBranch(submitted, context);
            if (completed != null) {
                orderedResults[submitted.index] = completed;
                continue;
            }
            submitted.future.cancel(true);
            orderedResults[submitted.index] = ContainerBranchExecutionSupport
                    .siblingInterruptedCancellationLog(submitted.branch, context, interruptingChild);
        }
    }

    private StationLogTrace cancelPendingForCancellation(List<SubmittedBranch> submissions,
                                                         StationLogTrace[] orderedResults,
                                                         StationExecutionContext context) {
        StationLogTrace firstCancellation = null;
        for (SubmittedBranch submitted : submissions) {
            if (orderedResults[submitted.index] != null) {
                continue;
            }
            submitted.future.cancel(true);
            StationLogTrace cancellation = ContainerBranchExecutionSupport.cooperativeCancellationLog(submitted.branch,
                                                                                                      context);
            orderedResults[submitted.index] = cancellation;
            if (firstCancellation == null) {
                firstCancellation = cancellation;
            }
        }
        return firstCancellation;
    }

    private void cancelPendingAfterUnexpectedInterruption(List<SubmittedBranch> submissions,
                                                          StationLogTrace[] orderedResults,
                                                          StationExecutionContext context) {
        for (SubmittedBranch submitted : submissions) {
            if (orderedResults[submitted.index] != null) {
                continue;
            }
            StationLogTrace completed = tryResolveAlreadyCompletedBranch(submitted, context);
            if (completed != null) {
                orderedResults[submitted.index] = completed;
                continue;
            }
            submitted.future.cancel(true);
            orderedResults[submitted.index] = ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch,
                                                                                                   context,
                                                                                                   new RuntimeException(
                                                                                                           "Branch cancelled because the container wait was interrupted"));
        }
    }

    private StationLogTrace tryResolveAlreadyCompletedBranch(SubmittedBranch submitted,
                                                             StationExecutionContext context) {
        if (!submitted.future.isDone() || submitted.future.isCancelled()) {
            return null;
        }
        try {
            BranchExecution execution = submitted.future.get();
            return ContainerBranchExecutionSupport.normalizeCompletedLog(execution.branch, execution.log, context);
        } catch (CancellationException e) {
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            return ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch, context, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while harvesting a completed branch", e);
        }
    }

    private record SubmittedBranch(int index, ContainerBaseStation.Branch<?> branch, Future<BranchExecution> future) {}

    private record BranchExecution(int index, ContainerBaseStation.Branch<?> branch, StationLogTrace log) {}
}
