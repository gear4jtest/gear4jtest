package io.github.gear4jtest.core.engine.strategy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object doExecute(
            ContainerBaseStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution) {
        FlowConfig config = FlowStrategySupport.resolveFlowConfig(station.getFlowConfig());
        List<StationLog> results = station.isParallel() && station.getExecutorService() != null
                ? executeParallelBranches(station, input, runner, operationExecution)
                : executeSequentialBranches(station, input, runner, operationExecution);

        List<Throwable> collectedErrors = new ArrayList<>();

        for (StationLog childLog : results) {
            FlowDecision decision = FlowDecider.decide(childLog, config);

            switch (decision) {
                case PROCEED -> {
                    // Rien à faire. On conserve le slot tel quel.
                }
                case MARK_AND_PROCEED -> collectedErrors.add(
                        FlowStrategySupport.representativeThrowable(
                                childLog,
                                "Container branch failed without exception: " + childLog.getOperationId()));
                case INTERRUPT -> {
                    FlowStrategySupport.applyInterruptToParentLog(operationExecution.getRecord(), childLog, config);
                    return null;
                }
            }
        }

        if (!collectedErrors.isEmpty()) {
            Throwable first = collectedErrors.get(0);
            operationExecution.getRecord().markFailed(
                    first instanceof Exception ex ? ex : new RuntimeException(first.getMessage(), first));
        }

        return returns(station, results);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<StationLog> executeSequentialBranches(
            ContainerBaseStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution) {

        List<StationLog> results = new ArrayList<>();

        for (ContainerBaseStation.Branch branch : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
            if (!isBranchConditionSatisfied(branch, input, operationExecution)) {
                results.add(buildConditionSkippedBranchLog(branch, operationExecution));
                continue;
            }

            Object newObject = deepClone(input);
            StationLog rec = runner.run(newObject, branch.getStation(), operationExecution);
            rec.setParentOperationId(operationExecution.getRecord().getId());
            results.add(rec);
        }

        return results;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<StationLog> executeParallelBranches(
            ContainerBaseStation station,
            Object input,
            StationRunner runner,
            StationExecutionContext operationExecution) {

        List<StationLog> results = new ArrayList<>();
        List<Callable<StationLog>> tasks = new ArrayList<>();
        List<ContainerBaseStation.Branch> scheduledBranches = new ArrayList<>();

        String currentItemId = operationExecution.getGlobalContext().getCurrentItemId();
        ExecutorService executor = operationExecution.getSupport()
                .executorFor(station.getExecutorService(), operationExecution.getGlobalContext());

        for (ContainerBaseStation.Branch branch : (List<ContainerBaseStation.Branch>) station.getPipelines()) {
            if (!isBranchConditionSatisfied(branch, input, operationExecution)) {
                results.add(buildConditionSkippedBranchLog(branch, operationExecution));
                continue;
            }

            tasks.add(operationExecution.getSupport()
                    .getTaskFactory()
                    .createTask(() -> deepClone(input), branch.getStation(), runner, operationExecution, currentItemId));
            scheduledBranches.add(branch);
        }

        if (tasks.isEmpty()) {
            return results;
        }

        try {
            List<Future<StationLog>> futures = invokeAll(executor, tasks, station.getAwaitTimeout());

            for (int i = 0; i < futures.size(); i++) {
                Future<StationLog> future = futures.get(i);
                ContainerBaseStation.Branch branch = scheduledBranches.get(i);

                if (future.isCancelled()) {
                    results.add(buildTimeoutCancelledBranchLog(branch, operationExecution, station.getAwaitTimeout()));
                    continue;
                }

                try {
                    StationLog value = future.get();
                    if (value != null) {
                        value.setParentOperationId(operationExecution.getRecord().getId());
                        results.add(value);
                    } else {
                        results.add(buildUnexpectedFailureBranchLog(
                                branch,
                                operationExecution,
                                new IllegalStateException("Parallel branch returned null StationLog")));
                    }
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Error error) {
                        throw error;
                    }

                    results.add(buildUnexpectedFailureBranchLog(branch, operationExecution, cause));
                }
            }

            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for container branches", e);
        }
    }

    private List<Future<StationLog>> invokeAll(
            ExecutorService executor,
            List<Callable<StationLog>> tasks,
            Duration awaitTimeout) throws InterruptedException {

        if (awaitTimeout == null) {
            return executor.invokeAll(tasks);
        }
        return executor.invokeAll(tasks, awaitTimeout.toMillis(), TimeUnit.MILLISECONDS);
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
    private boolean isBranchConditionSatisfied(
            ContainerBaseStation.Branch branch,
            Object input,
            StationExecutionContext operationExecution) {

        if (branch.getCondition() == null) {
            return true;
        }

        return branch.getCondition().test(input, operationExecution.getGlobalContext());
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
        log.markCancelled(new TimeoutException(
                "Container branch timed out after " + awaitTimeout));
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

    <T> T deepClone(T object) {
        return object;
    }
}
