package io.github.gear4jtest.core.api;

import java.util.Objects;

import io.github.gear4jtest.core.api.trace.RunTrace;

/**
 * Public result returned after a pipeline execution reaches a terminal state.
 *
 * <p>
 * A result distinguishes normal completion, functional skip, functional stop,
 * technical cancellation and normalized failure. Fatal JVM errors are not
 * expected to be converted into this type.
 * </p>
 */
public final class ExecutionResult<T> {
    private final T result;
    private final ExecutionOutcome outcome;
    private final RunTrace execution;
    private final Exception error;

    private ExecutionResult(T result, ExecutionOutcome outcome, RunTrace execution, Exception error) {
        this.result = result;
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.execution = execution;
        this.error = error;
    }

    /** Creates a normally completed execution result. */
    public static <OUT> ExecutionResult<OUT> success(OUT result, RunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.SUCCEEDED, exec, null);
    }

    /** Creates a functionally skipped execution result. */
    public static <OUT> ExecutionResult<OUT> skipped(OUT result, RunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.SKIPPED, exec, null);
    }

    /** Creates a functionally stopped execution result. */
    public static <OUT> ExecutionResult<OUT> stopped(OUT result, RunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.STOPPED, exec, null);
    }

    /** Creates a technically cancelled execution result. */
    public static <OUT> ExecutionResult<OUT> cancelled(OUT result, RunTrace exec, Exception error) {
        return new ExecutionResult<>(result, ExecutionOutcome.CANCELLED, exec, error);
    }

    /** Creates a failed execution result from a normalized exception. */
    public static <OUT> ExecutionResult<OUT> failure(Exception error, RunTrace exec) {
        return new ExecutionResult<>(null, ExecutionOutcome.FAILED, exec,
                Objects.requireNonNull(error, "error must not be null"));
    }

    public T getResult() {
        return result;
    }

    /** Returns true only when the pipeline completed successfully. */
    public boolean isSuccess() {
        return outcome == ExecutionOutcome.SUCCEEDED;
    }

    public boolean isStopped() {
        return outcome == ExecutionOutcome.STOPPED;
    }

    public boolean isSkipped() {
        return outcome == ExecutionOutcome.SKIPPED;
    }

    public boolean isCancelled() {
        return outcome == ExecutionOutcome.CANCELLED;
    }

    public boolean isFailed() {
        return outcome == ExecutionOutcome.FAILED;
    }

    public ExecutionOutcome getOutcome() {
        return outcome;
    }

    public RunTrace getExecution() {
        return execution;
    }

    public Exception getError() {
        return error;
    }
}
