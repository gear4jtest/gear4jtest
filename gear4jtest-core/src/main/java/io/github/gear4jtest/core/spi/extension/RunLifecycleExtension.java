package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.annotation.Spi;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;

/**
 * Passive lifecycle hooks around an assembly line run.
 *
 * <p>
 * These hooks bracket the measured runtime scope. Start callbacks run from
 * higher order to lower order after the official start time is assigned.
 * Completion callbacks run from lower order to higher order after the official
 * end time is assigned:
 * </p>
 *
 * <ul>
 * <li>{@link #onRunStarted(ExecutionContext, RunTrace)} is called after the run
 * trace has been marked {@code RUNNING} and after its {@code startTime} has
 * been assigned.</li>
 * <li>{@link #onRunCompleted(ExecutionContext, RunTrace)} is called after the
 * engine has finalized the current run state (status, result, error, final
 * context, end time). A critical completion failure is normalized back into a
 * failed {@code ExecutionResult} before later, higher-order observers run. Its
 * detection time becomes the final end time.</li>
 * </ul>
 *
 * <p>
 * Implementations must not mutate the lifecycle semantics of the run.
 * </p>
 */
@Spi
public interface RunLifecycleExtension extends RuntimeExtension {

    default LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.BEST_EFFORT;
    }

    default void onRunStarted(ExecutionContext ctx, RunTrace run) {
        // no-op
    }

    default void onRunCompleted(ExecutionContext ctx, RunTrace run) {
        // no-op
    }
}
