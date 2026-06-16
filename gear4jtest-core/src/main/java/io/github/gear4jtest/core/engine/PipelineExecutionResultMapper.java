package io.github.gear4jtest.core.engine;

import java.time.Instant;
import java.util.HashMap;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionOutcome;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PipelineExecutionResultMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineExecutionResultMapper.class);

    private PipelineExecutionResultMapper() {
    }

    @SuppressWarnings("unchecked")
    static <IN, OUT> ExecutionResult<OUT> executeRootStation(AssemblyLine<IN, OUT> pipeline,
                                                             RunRequest request,
                                                             StationRunner rootRunner,
                                                             StationExecutionContext rootContext,
                                                             AssemblyRunTrace execution) {
        StationLogTrace rootLog = rootRunner.run(request.getInput(), pipeline.getRootStation(), rootContext);
        Object result = rootLog.getOutput();

        return switch (rootLog.getStatus()) {
            case SUCCEEDED -> {
                execution.setStatus(ExecutionStatus.SUCCEEDED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.success(result, execution);
            }
            case SKIPPED -> {
                execution.setStatus(ExecutionStatus.SKIPPED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.skipped(result, execution);
            }
            case STOPPED -> {
                execution.setStatus(ExecutionStatus.STOPPED);
                execution.setResult(result);
                yield (ExecutionResult<OUT>) ExecutionResult.stopped(result, execution);
            }
            case CANCELLED -> {
                Exception cancellation = rootLog.getErrorMessage() != null
                        ? new RuntimeException(rootLog.getErrorMessage()) : null;
                execution.setStatus(ExecutionStatus.CANCELLED);
                execution.setResult(result);
                if (cancellation != null) {
                    execution.setError(cancellation);
                }
                yield (ExecutionResult<OUT>) ExecutionResult.cancelled(result, execution, cancellation);
            }
            case FAILED, RUNNING -> {
                Exception failure = new RuntimeException(
                        rootLog.getErrorMessage() != null ? rootLog.getErrorMessage() : "Pipeline failed");
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setError(failure);
                yield ExecutionResult.failure(failure, execution);
            }
        };
    }

    static void finalizeRunFromResult(ExecutionContext context,
                                      AssemblyRunTrace execution,
                                      ExecutionResult<?> result,
                                      Throwable fatalError) {
        if (execution.getEndTime() == null) {
            execution.setEndTime(Instant.now());
        }

        try {
            execution.setContext(new HashMap<>(context.getContext()));
        } catch (Throwable throwable) {
            LOGGER.warn("Failed to capture execution context for run {}. The run trace will keep its previous context.",
                        execution.getId(), throwable);
        }

        if (fatalError != null) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage("CRITICAL JVM ERROR: " + fatalError);
        } else if (result != null) {
            execution.setResult(result.getResult());
            switch (result.getOutcome()) {
                case SUCCEEDED -> execution.setStatus(ExecutionStatus.SUCCEEDED);
                case SKIPPED -> execution.setStatus(ExecutionStatus.SKIPPED);
                case STOPPED -> execution.setStatus(ExecutionStatus.STOPPED);
                case CANCELLED -> execution.setStatus(ExecutionStatus.CANCELLED);
                case FAILED -> execution.setStatus(ExecutionStatus.FAILED);
            }
            if (result.getOutcome() == ExecutionOutcome.FAILED || result.getOutcome() == ExecutionOutcome.CANCELLED) {
                if (result.getError() != null) {
                    execution.setError(asException(result.getError()));
                }
            }
        } else {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setError(new IllegalStateException("Pipeline execution returned no result"));
        }
    }

    static Exception asException(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new RuntimeException(throwable);
    }
}
