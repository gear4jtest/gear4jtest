package io.github.gear4jtest.core.api;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.trace.RunTrace;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Public result returned after a pipeline execution reaches a terminal state.
 *
 * <p>
 * A result distinguishes normal completion, functional skip, functional stop,
 * technical cancellation and normalized failure. Fatal JVM errors are not
 * expected to be converted into this type.
 * </p>
 */
@NullMarked
public final class ExecutionResult<T> {
    private final @Nullable T result;
    private final ExecutionOutcome outcome;
    private final @Nullable RunTrace execution;
    private final @Nullable Exception error;

    private ExecutionResult(@Nullable T result,
                            ExecutionOutcome outcome,
                            @Nullable RunTrace execution,
                            @Nullable Exception error) {
        this.result = result;
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.execution = execution;
        this.error = error;
    }

    /** Creates a normally completed execution result. */
    public static <OUT> ExecutionResult<OUT> success(@Nullable OUT result, @Nullable RunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.SUCCEEDED, exec, null);
    }

    /** Creates a functionally skipped execution result. */
    public static <OUT> ExecutionResult<OUT> skipped(@Nullable OUT result, @Nullable RunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.SKIPPED, exec, null);
    }

    /** Creates a functionally stopped execution result. */
    public static <OUT> ExecutionResult<OUT> stopped(@Nullable OUT result, @Nullable RunTrace exec) {
        return new ExecutionResult<>(result, ExecutionOutcome.STOPPED, exec, null);
    }

    /** Creates a technically cancelled execution result. */
    public static <OUT> ExecutionResult<OUT> cancelled(@Nullable OUT result,
                                                       @Nullable RunTrace exec,
                                                       @Nullable Exception error) {
        return new ExecutionResult<>(result, ExecutionOutcome.CANCELLED, exec, error);
    }

    /** Creates a failed execution result from a normalized exception. */
    public static <OUT> ExecutionResult<OUT> failure(Exception error, @Nullable RunTrace exec) {
        return new ExecutionResult<>(null, ExecutionOutcome.FAILED, exec,
                Objects.requireNonNull(error, "error must not be null"));
    }

    /**
     * Returns the output, or {@code null} when no output is available. The outcome
     * must be inspected independently because a successful operator may itself
     * return {@code null}.
     */
    public @Nullable T getResult() {
        return result;
    }

    /** Returns the output as an optional, independently from the outcome. */
    public Optional<T> resultOptional() {
        return Optional.ofNullable(result);
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

    /**
     * Returns the run trace, or {@code null} for a manually-created result that has
     * no trace. Results returned by the standard engine after a run has started
     * carry a trace.
     */
    public @Nullable RunTrace getExecution() {
        return execution;
    }

    /** Returns the run trace as an optional. */
    public Optional<RunTrace> executionOptional() {
        return Optional.ofNullable(execution);
    }

    /**
     * Returns the normalized failure/cancellation exception, or {@code null} when
     * no error is available. Failed results always carry an error; cancellation
     * results may omit it.
     */
    public @Nullable Exception getError() {
        return error;
    }

    /** Returns the normalized failure/cancellation exception as an optional. */
    public Optional<Exception> errorOptional() {
        return Optional.ofNullable(error);
    }
}
