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
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import io.github.gear4jtest.core.util.MonotonicDeadline;

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
        List<? extends ContainerBaseStation.Branch<?>> branches = station.getAssemblyLines();
        BranchOutcome[] outcomes = initializeOutcomes(branches.size());
        List<Throwable> collectedErrors = new ArrayList<>();
        String currentItemId = context.getGlobalContext().getCurrentItemId();
        ExecutorService executor = EngineStationContexts.support(context).executorFor(station.getExecutorService(),
                                                                                      context.getGlobalContext());
        CompletionService<BranchExecution> completionService = new ExecutorCompletionService<>(executor);
        List<SubmittedBranch> submittedBranches = new ArrayList<>();
        Map<Future<BranchExecution>, SubmittedBranch> submittedByFuture = new IdentityHashMap<>();

        for (int index = 0; index < branches.size(); index++) {
            ContainerBaseStation.Branch<?> branch = branches.get(index);
            if (!ContainerBranchExecutionSupport.isBranchConditionSatisfied(branch, input, context)) {
                recordOutcome(outcomes, index, BranchState.SKIPPED,
                              ContainerBranchExecutionSupport.conditionSkippedLog(branch, input, context,
                                                                                  StationSkipReason.CONDITION_NOT_SATISFIED));
                continue;
            }
            if (context.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                StationLogTrace cancellation = ContainerBranchExecutionSupport.cooperativeCancellationLog(branch,
                                                                                                          input,
                                                                                                          context);
                recordOutcome(outcomes, index, BranchState.CANCELLED, cancellation);
                cancelPendingForCancellation(submittedBranches, outcomes, input, context);
                cancelRemainingBeforeSubmission(branches, index + 1, outcomes, input, context);
                return new ContainerExecutionAggregation(
                        asOrderedList(branches, outcomes, input, context), collectedErrors, cancellation);
            }

            Callable<StationLogTrace> task = EngineStationContexts.support(context).getTaskFactory()
                    .createTask(() -> ContainerBranchExecutionSupport.clonePayload(input, context),
                                branch.getStation(), runner, context, currentItemId, branch.getId());
            int finalIndex = index;
            try {
                Future<BranchExecution> future = completionService
                        .submit(() -> new BranchExecution(finalIndex, branch, task.call()));
                SubmittedBranch submitted = new SubmittedBranch(index, branch, future);
                submittedBranches.add(submitted);
                submittedByFuture.put(future, submitted);
                outcomes[index] = BranchOutcome.submitted();
            } catch (RejectedExecutionException rejected) {
                StationLogTrace rejectedLog = ContainerBranchExecutionSupport.unexpectedFailureLog(branch, input,
                                                                                                   context,
                                                                                                   rejected);
                recordOutcome(outcomes, index, BranchState.REJECTED, rejectedLog);
                FlowDecision decision = FlowDecider.decide(rejectedLog, flowConfig);
                if (decision == FlowDecision.MARK_AND_PROCEED) {
                    collectedErrors.add(rejected);
                } else if (decision == FlowDecision.INTERRUPT) {
                    cancelPendingAfterInterrupt(submittedBranches, outcomes, input, context, rejectedLog);
                    interruptRemainingBeforeSubmission(branches, index + 1, outcomes, input, context, rejectedLog);
                    return new ContainerExecutionAggregation(
                            asOrderedList(branches, outcomes, input, context), collectedErrors, rejectedLog);
                }
            }
        }

        if (submittedBranches.isEmpty()) {
            return new ContainerExecutionAggregation(asOrderedList(branches, outcomes, input, context),
                    collectedErrors, null);
        }

        MonotonicDeadline deadline = MonotonicDeadline.start(awaitTimeout);
        int completedCount = 0;
        try {
            while (completedCount < submittedBranches.size()) {
                if (context.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                    StationLogTrace cancellation = cancelPendingForCancellation(submittedBranches, outcomes,
                                                                                input,
                                                                                context);
                    return new ContainerExecutionAggregation(
                            asOrderedList(branches, outcomes, input, context), collectedErrors, cancellation);
                }

                Future<BranchExecution> completedFuture = waitForNextCompletion(completionService, deadline);
                if (completedFuture == null) {
                    StationLogTrace timeoutChild = timeoutPendingBranches(submittedBranches, outcomes, input,
                                                                          context,
                                                                          awaitTimeout);
                    if (timeoutChild != null
                            && FlowDecider.decide(timeoutChild, flowConfig) == FlowDecision.INTERRUPT) {
                        return new ContainerExecutionAggregation(
                                asOrderedList(branches, outcomes, input, context), collectedErrors, timeoutChild);
                    }
                    return new ContainerExecutionAggregation(asOrderedList(branches, outcomes, input, context),
                            collectedErrors, null);
                }

                SubmittedBranch submitted = submittedByFuture.get(completedFuture);
                BranchExecution execution = readCompletedExecution(completedFuture, submitted, input, context);
                completedCount++;
                StationLogTrace childLog = ContainerBranchExecutionSupport.normalizeCompletedLog(execution.branch,
                                                                                                 execution.log, input,
                                                                                                 context);
                recordOutcome(outcomes, execution.index, BranchState.COMPLETED, childLog);
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
                        cancelPendingAfterInterrupt(submittedBranches, outcomes, input, context, childLog);
                        return new ContainerExecutionAggregation(
                                asOrderedList(branches, outcomes, input, context), collectedErrors, childLog);
                    }
                }
            }
            return new ContainerExecutionAggregation(asOrderedList(branches, outcomes, input, context),
                    collectedErrors, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelPendingAfterUnexpectedInterruption(submittedBranches, outcomes, input, context);
            throw new RuntimeException("Interrupted while waiting for container branches", e);
        }
    }

    private Future<BranchExecution> waitForNextCompletion(CompletionService<BranchExecution> completionService,
                                                          MonotonicDeadline deadline)
            throws InterruptedException {
        long remainingNanos = deadline.remainingNanos();
        return remainingNanos <= 0L ? null : completionService.poll(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private BranchExecution readCompletedExecution(Future<BranchExecution> future,
                                                   SubmittedBranch submitted,
                                                   Object input,
                                                   StationExecutionContext context) {
        try {
            return future.get();
        } catch (CancellationException e) {
            return new BranchExecution(submitted.index, submitted.branch,
                    ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch, input, context,
                                                                         new RuntimeException(
                                                                                 "Completed branch future was cancelled unexpectedly",
                                                                                 e)));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            return new BranchExecution(submitted.index, submitted.branch,
                    ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch, input, context, cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while reading a completed branch", e);
        }
    }

    private StationLogTrace timeoutPendingBranches(List<SubmittedBranch> submissions,
                                                   BranchOutcome[] outcomes,
                                                   Object input,
                                                   StationExecutionContext context,
                                                   Duration awaitTimeout) {
        StationLogTrace firstTimeoutLog = null;
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            StationLogTrace completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed != null) {
                recordOutcome(outcomes, submitted.index, BranchState.COMPLETED, completed);
                continue;
            }
            StationLogTrace timeout = ContainerBranchExecutionSupport.timeoutCancelledLog(submitted.branch, input,
                                                                                          context,
                                                                                          awaitTimeout);
            recordOutcome(outcomes, submitted.index, BranchState.CANCELLED, timeout);
            if (firstTimeoutLog == null) {
                firstTimeoutLog = timeout;
            }
        }
        return firstTimeoutLog;
    }

    private void cancelPendingAfterInterrupt(List<SubmittedBranch> submissions,
                                             BranchOutcome[] outcomes,
                                             Object input,
                                             StationExecutionContext context,
                                             StationLogTrace interruptingChild) {
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            StationLogTrace completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed != null) {
                recordOutcome(outcomes, submitted.index, BranchState.COMPLETED, completed);
                continue;
            }
            recordOutcome(outcomes, submitted.index, BranchState.INTERRUPTED,
                          ContainerBranchExecutionSupport.siblingInterruptedCancellationLog(submitted.branch, input,
                                                                                            context,
                                                                                            interruptingChild));
        }
    }

    private StationLogTrace cancelPendingForCancellation(List<SubmittedBranch> submissions,
                                                         BranchOutcome[] outcomes,
                                                         Object input,
                                                         StationExecutionContext context) {
        StationLogTrace firstCancellation = null;
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            StationLogTrace completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed != null) {
                recordOutcome(outcomes, submitted.index, BranchState.COMPLETED, completed);
                continue;
            }
            StationLogTrace cancellation = ContainerBranchExecutionSupport.cooperativeCancellationLog(submitted.branch,
                                                                                                      input, context);
            recordOutcome(outcomes, submitted.index, BranchState.CANCELLED, cancellation);
            if (firstCancellation == null) {
                firstCancellation = cancellation;
            }
        }
        return firstCancellation;
    }

    private void cancelPendingAfterUnexpectedInterruption(List<SubmittedBranch> submissions,
                                                          BranchOutcome[] outcomes,
                                                          Object input,
                                                          StationExecutionContext context) {
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            StationLogTrace completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed != null) {
                recordOutcome(outcomes, submitted.index, BranchState.COMPLETED, completed);
                continue;
            }
            recordOutcome(outcomes, submitted.index, BranchState.CANCELLED,
                          ContainerBranchExecutionSupport.waitInterruptedCancellationLog(submitted.branch, input,
                                                                                         context));
        }
    }

    private void cancelRemainingBeforeSubmission(List<? extends ContainerBaseStation.Branch<?>> branches,
                                                 int firstRemainingIndex,
                                                 BranchOutcome[] outcomes,
                                                 Object input,
                                                 StationExecutionContext context) {
        for (int index = firstRemainingIndex; index < branches.size(); index++) {
            ContainerBaseStation.Branch<?> branch = branches.get(index);
            recordOutcome(outcomes, index, BranchState.CANCELLED,
                          ContainerBranchExecutionSupport.cancelledBeforeSubmissionLog(branch, input, context));
        }
    }

    private void interruptRemainingBeforeSubmission(List<? extends ContainerBaseStation.Branch<?>> branches,
                                                    int firstRemainingIndex,
                                                    BranchOutcome[] outcomes,
                                                    Object input,
                                                    StationExecutionContext context,
                                                    StationLogTrace interruptingChild) {
        for (int index = firstRemainingIndex; index < branches.size(); index++) {
            ContainerBaseStation.Branch<?> branch = branches.get(index);
            recordOutcome(outcomes, index, BranchState.INTERRUPTED,
                          ContainerBranchExecutionSupport.siblingInterruptedCancellationLog(branch, input, context,
                                                                                            interruptingChild));
        }
    }

    private StationLogTrace tryResolveCompletedAroundCancellation(SubmittedBranch submitted,
                                                                  Object input,
                                                                  StationExecutionContext context) {
        StationLogTrace completed = tryResolveAlreadyCompletedBranch(submitted, input, context);
        if (completed != null) {
            return completed;
        }
        if (submitted.future.cancel(true)) {
            return null;
        }
        return tryResolveAlreadyCompletedBranch(submitted, input, context);
    }

    private StationLogTrace tryResolveAlreadyCompletedBranch(SubmittedBranch submitted,
                                                             Object input,
                                                             StationExecutionContext context) {
        if (!submitted.future.isDone() || submitted.future.isCancelled()) {
            return null;
        }
        try {
            BranchExecution execution = submitted.future.get();
            return ContainerBranchExecutionSupport.normalizeCompletedLog(execution.branch, execution.log, input,
                                                                         context);
        } catch (CancellationException e) {
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            return ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch, input, context, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while harvesting a completed branch", e);
        }
    }

    private static BranchOutcome[] initializeOutcomes(int branchCount) {
        BranchOutcome[] outcomes = new BranchOutcome[branchCount];
        for (int index = 0; index < branchCount; index++) {
            outcomes[index] = BranchOutcome.notVisited();
        }
        return outcomes;
    }

    private static void recordOutcome(BranchOutcome[] outcomes,
                                      int index,
                                      BranchState state,
                                      StationLogTrace log) {
        outcomes[index] = BranchOutcome.terminal(state, log);
    }

    private static List<StationLogTrace> asOrderedList(List<? extends ContainerBaseStation.Branch<?>> branches,
                                                       BranchOutcome[] outcomes,
                                                       Object input,
                                                       StationExecutionContext context) {
        List<StationLogTrace> results = new ArrayList<>(outcomes.length);
        for (int index = 0; index < outcomes.length; index++) {
            BranchOutcome outcome = outcomes[index];
            if (!outcome.isTerminal()) {
                StationLogTrace invariantFailure = ContainerBranchExecutionSupport.unexpectedFailureLog(
                                                                                                        branches.get(index),
                                                                                                        input, context,
                                                                                                        new IllegalStateException(
                                                                                                                "Missing container branch result at index "
                                                                                                                        + index
                                                                                                                        + " with state "
                                                                                                                        + outcome.state));
                outcome = BranchOutcome.terminal(BranchState.INVARIANT_FAILURE, invariantFailure);
                outcomes[index] = outcome;
            }
            results.add(outcome.log);
        }
        return results;
    }

    private record SubmittedBranch(int index, ContainerBaseStation.Branch<?> branch, Future<BranchExecution> future) {}

    private record BranchExecution(int index, ContainerBaseStation.Branch<?> branch, StationLogTrace log) {}

    private record BranchOutcome(BranchState state, StationLogTrace log) {
        private static BranchOutcome notVisited() {
            return new BranchOutcome(BranchState.NOT_VISITED, null);
        }

        private static BranchOutcome submitted() {
            return new BranchOutcome(BranchState.SUBMITTED, null);
        }

        private static BranchOutcome terminal(BranchState state, StationLogTrace log) {
            return new BranchOutcome(state, log);
        }

        private boolean isTerminal() {
            return state.terminal;
        }
    }

    private enum BranchState {
        NOT_VISITED(false),
        SUBMITTED(false),
        COMPLETED(true),
        SKIPPED(true),
        CANCELLED(true),
        REJECTED(true),
        INTERRUPTED(true),
        INVARIANT_FAILURE(true);

        private final boolean terminal;

        BranchState(boolean terminal) {
            this.terminal = terminal;
        }
    }
}
