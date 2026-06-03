package io.github.gear4jtest.core.api;

import java.util.Objects;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;

/**
 * Public result returned after a pipeline execution reaches a terminal state.
 *
 * <p>
 * A result distinguishes normal completion, functional stop, technical
 * cancellation and normalized failure. Fatal JVM errors are not expected to be
 * converted into this type.
 * </p>
 */
public class ExecutionResult<T> {
    private final T result;
    private final ExecutionOutcome outcome;
    private final AssemblyRunTrace execution;
    private final Exception error;

    /**
     * Legacy constructor maintained for source compatibility. Prefer the factory
     * methods or
     * {@link #ExecutionResult(Object, ExecutionOutcome, AssemblyRunTrace, Exception)}.
     */
    @Deprecated(since = "1.0.0", forRemoval = false)
    public ExecutionResult(T result, boolean success, AssemblyRunTrace execution, Exception error) {
        this(result, success ? ExecutionOutcome.SUCCEEDED : ExecutionOutcome.FAILED, execution, error);
    }

    public ExecutionResult(T result, ExecutionOutcome outcome, AssemblyRunTrace execution, Exception error) {
        this.result = result;
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.execution = execution;
        this.error = error;
    }

    /** Creates a normally completed execution result. */
    public static <OUT> ExecutionResult<OUT> success(OUT result, AssemblyRunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.SUCCEEDED, exec, null);
    }

    /** Creates a functionally stopped execution result. */
    public static <OUT> ExecutionResult<OUT> stopped(OUT result, AssemblyRunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.STOPPED, exec, null);
    }

    /** Creates a technically cancelled execution result. */
    public static <OUT> ExecutionResult<OUT> cancelled(OUT result, AssemblyRunTrace exec, Exception error) {
        return new ExecutionResult<>(result, ExecutionOutcome.CANCELLED, exec, error);
    }

    /** Creates a failed execution result from a normalized exception. */
    public static <OUT> ExecutionResult<OUT> failure(Exception error, AssemblyRunTrace exec) {
        return new ExecutionResult<>(null, ExecutionOutcome.FAILED, exec, error);
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

    public boolean isCancelled() {
        return outcome == ExecutionOutcome.CANCELLED;
    }

    public boolean isFailed() {
        return outcome == ExecutionOutcome.FAILED;
    }

    public ExecutionOutcome getOutcome() {
        return outcome;
    }

    public AssemblyRunTrace getExecution() {
        return execution;
    }

    public Exception getError() {
        return error;
    }
}
