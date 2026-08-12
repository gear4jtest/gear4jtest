package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        ParallelBranchOutcome[] outcomes = initializeOutcomes(branches.size());
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
                recordOutcome(outcomes, index, ParallelBranchOutcome.State.SKIPPED,
                              ContainerBranchExecutionSupport.conditionSkippedLog(branch, input, context,
                                                                                  StationSkipReason.CONDITION_NOT_SATISFIED));
                continue;
            }
            if (context.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                StationLogTrace cancellation = ContainerBranchExecutionSupport.cooperativeCancellationLog(branch,
                                                                                                          input,
                                                                                                          context);
                recordOutcome(outcomes, index, ParallelBranchOutcome.State.CANCELLED, cancellation);
                cancelPendingForCancellation(submittedBranches, outcomes, input, context);
                cancelRemainingBeforeSubmission(branches, index + 1, outcomes, input, context);
                return ContainerExecutionAggregation.interrupted(
                                                                 asOrderedList(branches, outcomes, input, context),
                                                                 collectedErrors, cancellation);
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
                outcomes[index] = ParallelBranchOutcome.submitted();
            } catch (RejectedExecutionException rejected) {
                StationLogTrace rejectedLog = ContainerBranchExecutionSupport.unexpectedFailureLog(branch, input,
                                                                                                   context,
                                                                                                   rejected);
                recordOutcome(outcomes, index, ParallelBranchOutcome.State.REJECTED, rejectedLog);
                FlowDecision decision = FlowDecider.decide(rejectedLog, flowConfig);
                if (decision == FlowDecision.MARK_AND_PROCEED) {
                    collectedErrors.add(rejected);
                } else if (decision == FlowDecision.INTERRUPT) {
                    cancelPendingAfterInterrupt(submittedBranches, outcomes, input, context, rejectedLog);
                    interruptRemainingBeforeSubmission(branches, index + 1, outcomes, input, context, rejectedLog);
                    return ContainerExecutionAggregation.interrupted(
                                                                     asOrderedList(branches, outcomes, input, context),
                                                                     collectedErrors, rejectedLog);
                }
            }
        }

        if (submittedBranches.isEmpty()) {
            return ContainerExecutionAggregation.completed(asOrderedList(branches, outcomes, input, context),
                                                           collectedErrors);
        }

        MonotonicDeadline deadline = MonotonicDeadline.start(awaitTimeout);
        int completedCount = 0;
        try {
            while (completedCount < submittedBranches.size()) {
                if (context.getGlobalContext().getCancellationToken().isCancellationRequested()) {
                    Optional<StationLogTrace> cancellation = cancelPendingForCancellation(submittedBranches, outcomes,
                                                                                          input, context);
                    return aggregate(branches, outcomes, input, context, collectedErrors, cancellation);
                }

                Optional<Future<BranchExecution>> completedFuture = waitForNextCompletion(completionService, deadline);
                if (completedFuture.isEmpty()) {
                    Optional<StationLogTrace> timeoutChild = timeoutPendingBranches(submittedBranches, outcomes, input,
                                                                                    context, awaitTimeout);
                    if (timeoutChild.isPresent()
                            && FlowDecider.decide(timeoutChild.orElseThrow(), flowConfig) == FlowDecision.INTERRUPT) {
                        return ContainerExecutionAggregation.interrupted(
                                                                         asOrderedList(branches, outcomes, input,
                                                                                       context),
                                                                         collectedErrors,
                                                                         timeoutChild.orElseThrow());
                    }
                    return ContainerExecutionAggregation.completed(asOrderedList(branches, outcomes, input, context),
                                                                   collectedErrors);
                }

                Future<BranchExecution> completedBranchFuture = completedFuture.orElseThrow();
                SubmittedBranch submitted = submittedByFuture.get(completedBranchFuture);
                BranchExecution execution = readCompletedExecution(completedBranchFuture, submitted, input, context);
                completedCount++;
                StationLogTrace childLog = ContainerBranchExecutionSupport.normalizeCompletedLog(execution.branch,
                                                                                                 execution.log, input,
                                                                                                 context);
                recordOutcome(outcomes, execution.index, ParallelBranchOutcome.State.COMPLETED, childLog);
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
                        return ContainerExecutionAggregation.interrupted(
                                                                         asOrderedList(branches, outcomes, input,
                                                                                       context),
                                                                         collectedErrors, childLog);
                    }
                }
            }
            return ContainerExecutionAggregation.completed(asOrderedList(branches, outcomes, input, context),
                                                           collectedErrors);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelPendingAfterUnexpectedInterruption(submittedBranches, outcomes, input, context);
            throw new RuntimeException("Interrupted while waiting for container branches", e);
        }
    }

    private Optional<Future<BranchExecution>> waitForNextCompletion(CompletionService<BranchExecution> completionService,
                                                                    MonotonicDeadline deadline)
            throws InterruptedException {
        long remainingNanos = deadline.remainingNanos();
        return remainingNanos <= 0L ? Optional.empty()
                : Optional.ofNullable(completionService.poll(remainingNanos, TimeUnit.NANOSECONDS));
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

    private Optional<StationLogTrace> timeoutPendingBranches(List<SubmittedBranch> submissions,
                                                             ParallelBranchOutcome[] outcomes,
                                                             Object input,
                                                             StationExecutionContext context,
                                                             Duration awaitTimeout) {
        Optional<StationLogTrace> firstTimeoutLog = Optional.empty();
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            Optional<StationLogTrace> completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed.isPresent()) {
                recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.COMPLETED,
                              completed.orElseThrow());
                continue;
            }
            StationLogTrace timeout = ContainerBranchExecutionSupport.timeoutCancelledLog(submitted.branch, input,
                                                                                          context,
                                                                                          awaitTimeout);
            recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.CANCELLED, timeout);
            if (firstTimeoutLog.isEmpty()) {
                firstTimeoutLog = Optional.of(timeout);
            }
        }
        return firstTimeoutLog;
    }

    private void cancelPendingAfterInterrupt(List<SubmittedBranch> submissions,
                                             ParallelBranchOutcome[] outcomes,
                                             Object input,
                                             StationExecutionContext context,
                                             StationLogTrace interruptingChild) {
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            Optional<StationLogTrace> completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed.isPresent()) {
                recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.COMPLETED,
                              completed.orElseThrow());
                continue;
            }
            recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.INTERRUPTED,
                          ContainerBranchExecutionSupport.siblingInterruptedCancellationLog(submitted.branch, input,
                                                                                            context,
                                                                                            interruptingChild));
        }
    }

    private Optional<StationLogTrace> cancelPendingForCancellation(List<SubmittedBranch> submissions,
                                                                   ParallelBranchOutcome[] outcomes,
                                                                   Object input,
                                                                   StationExecutionContext context) {
        Optional<StationLogTrace> firstCancellation = Optional.empty();
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            Optional<StationLogTrace> completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed.isPresent()) {
                recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.COMPLETED,
                              completed.orElseThrow());
                continue;
            }
            StationLogTrace cancellation = ContainerBranchExecutionSupport.cooperativeCancellationLog(submitted.branch,
                                                                                                      input, context);
            recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.CANCELLED, cancellation);
            if (firstCancellation.isEmpty()) {
                firstCancellation = Optional.of(cancellation);
            }
        }
        return firstCancellation;
    }

    private void cancelPendingAfterUnexpectedInterruption(List<SubmittedBranch> submissions,
                                                          ParallelBranchOutcome[] outcomes,
                                                          Object input,
                                                          StationExecutionContext context) {
        for (SubmittedBranch submitted : submissions) {
            if (outcomes[submitted.index].isTerminal()) {
                continue;
            }
            Optional<StationLogTrace> completed = tryResolveCompletedAroundCancellation(submitted, input, context);
            if (completed.isPresent()) {
                recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.COMPLETED,
                              completed.orElseThrow());
                continue;
            }
            recordOutcome(outcomes, submitted.index, ParallelBranchOutcome.State.CANCELLED,
                          ContainerBranchExecutionSupport.waitInterruptedCancellationLog(submitted.branch, input,
                                                                                         context));
        }
    }

    private void cancelRemainingBeforeSubmission(List<? extends ContainerBaseStation.Branch<?>> branches,
                                                 int firstRemainingIndex,
                                                 ParallelBranchOutcome[] outcomes,
                                                 Object input,
                                                 StationExecutionContext context) {
        for (int index = firstRemainingIndex; index < branches.size(); index++) {
            ContainerBaseStation.Branch<?> branch = branches.get(index);
            recordOutcome(outcomes, index, ParallelBranchOutcome.State.CANCELLED,
                          ContainerBranchExecutionSupport.cancelledBeforeSubmissionLog(branch, input, context));
        }
    }

    private void interruptRemainingBeforeSubmission(List<? extends ContainerBaseStation.Branch<?>> branches,
                                                    int firstRemainingIndex,
                                                    ParallelBranchOutcome[] outcomes,
                                                    Object input,
                                                    StationExecutionContext context,
                                                    StationLogTrace interruptingChild) {
        for (int index = firstRemainingIndex; index < branches.size(); index++) {
            ContainerBaseStation.Branch<?> branch = branches.get(index);
            recordOutcome(outcomes, index, ParallelBranchOutcome.State.INTERRUPTED,
                          ContainerBranchExecutionSupport.siblingInterruptedCancellationLog(branch, input, context,
                                                                                            interruptingChild));
        }
    }

    private Optional<StationLogTrace> tryResolveCompletedAroundCancellation(SubmittedBranch submitted,
                                                                            Object input,
                                                                            StationExecutionContext context) {
        Optional<StationLogTrace> completed = tryResolveAlreadyCompletedBranch(submitted, input, context);
        if (completed.isPresent()) {
            return completed;
        }
        if (submitted.future.cancel(true)) {
            return Optional.empty();
        }
        return tryResolveAlreadyCompletedBranch(submitted, input, context);
    }

    private Optional<StationLogTrace> tryResolveAlreadyCompletedBranch(SubmittedBranch submitted,
                                                                       Object input,
                                                                       StationExecutionContext context) {
        if (!submitted.future.isDone() || submitted.future.isCancelled()) {
            return Optional.empty();
        }
        try {
            BranchExecution execution = submitted.future.get();
            return Optional.of(ContainerBranchExecutionSupport.normalizeCompletedLog(execution.branch, execution.log,
                                                                                     input, context));
        } catch (CancellationException e) {
            return Optional.empty();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            return Optional.of(
                               ContainerBranchExecutionSupport.unexpectedFailureLog(submitted.branch, input, context,
                                                                                    cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while harvesting a completed branch", e);
        }
    }

    private static ParallelBranchOutcome[] initializeOutcomes(int branchCount) {
        ParallelBranchOutcome[] outcomes = new ParallelBranchOutcome[branchCount];
        for (int index = 0; index < branchCount; index++) {
            outcomes[index] = ParallelBranchOutcome.notVisited();
        }
        return outcomes;
    }

    private static void recordOutcome(ParallelBranchOutcome[] outcomes,
                                      int index,
                                      ParallelBranchOutcome.State state,
                                      StationLogTrace log) {
        outcomes[index] = ParallelBranchOutcome.terminal(state, log);
    }

    private static List<StationLogTrace> asOrderedList(List<? extends ContainerBaseStation.Branch<?>> branches,
                                                       ParallelBranchOutcome[] outcomes,
                                                       Object input,
                                                       StationExecutionContext context) {
        List<StationLogTrace> results = new ArrayList<>(outcomes.length);
        for (int index = 0; index < outcomes.length; index++) {
            ParallelBranchOutcome outcome = outcomes[index];
            if (!outcome.isTerminal()) {
                StationLogTrace invariantFailure = ContainerBranchExecutionSupport.unexpectedFailureLog(
                                                                                                        branches.get(index),
                                                                                                        input, context,
                                                                                                        new IllegalStateException(
                                                                                                                "Missing container branch result at index "
                                                                                                                        + index
                                                                                                                        + " with state "
                                                                                                                        + outcome
                                                                                                                                .state()));
                outcome = ParallelBranchOutcome.terminal(ParallelBranchOutcome.State.INVARIANT_FAILURE,
                                                         invariantFailure);
                outcomes[index] = outcome;
            }
            results.add(outcome.requireLog());
        }
        return results;
    }

    private static ContainerExecutionAggregation aggregate(List<? extends ContainerBaseStation.Branch<?>> branches,
                                                           ParallelBranchOutcome[] outcomes,
                                                           Object input,
                                                           StationExecutionContext context,
                                                           List<Throwable> collectedErrors,
                                                           Optional<StationLogTrace> interruptingChild) {
        List<StationLogTrace> orderedResults = asOrderedList(branches, outcomes, input, context);
        return interruptingChild
                .map(child -> ContainerExecutionAggregation.interrupted(orderedResults, collectedErrors, child))
                .orElseGet(() -> ContainerExecutionAggregation.completed(orderedResults, collectedErrors));
    }

    private record SubmittedBranch(int index, ContainerBaseStation.Branch<?> branch, Future<BranchExecution> future) {}

    private record BranchExecution(int index, ContainerBaseStation.Branch<?> branch, StationLogTrace log) {}
}
