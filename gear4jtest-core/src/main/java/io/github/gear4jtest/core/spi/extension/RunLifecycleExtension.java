package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;

/**
 * Passive lifecycle hooks around a pipeline run.
 *
 * <p>
 * These hooks are invoked by the engine outside the measured runtime scope:
 * </p>
 *
 * <ul>
 * <li>{@link #onRunStarted(ExecutionContext, AssemblyRunTrace)} is called after
 * the run trace has been marked {@code RUNNING} and after its {@code startTime}
 * has been assigned.</li>
 * <li>{@link #onRunCompleted(ExecutionContext, AssemblyRunTrace)} is called
 * after the engine has fully finalized the run (status, result, error, final
 * context, end time). A critical completion failure is normalized back into a
 * failed {@code ExecutionResult} instead of escaping as a raw hook
 * exception.</li>
 * </ul>
 *
 * <p>
 * Implementations must not mutate the lifecycle semantics of the run.
 * </p>
 */
public interface RunLifecycleExtension extends RuntimeExtension {
    default LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.BEST_EFFORT;
    }

    default void onRunStarted(ExecutionContext ctx, AssemblyRunTrace run) {
        // no-op
    }

    default void onRunCompleted(ExecutionContext ctx, AssemblyRunTrace run) {
        // no-op
    }
}
