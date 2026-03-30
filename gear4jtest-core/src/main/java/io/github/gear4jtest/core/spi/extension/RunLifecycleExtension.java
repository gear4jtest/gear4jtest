package io.github.gear4jtest.core.spi.extension;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.persistence.AssemblyRun;

/**
 * Passive lifecycle hooks around a pipeline run.
 *
 * <p>These hooks are invoked by the engine outside the measured runtime scope:
 *
 * <ul>
 *   <li>{@link #onRunStarted(ExecutionContext, AssemblyRun)} is called before the engine starts the
 *       runtime timer.</li>
 *   <li>{@link #onRunCompleted(ExecutionContext, AssemblyRun)} is called after the engine has fully
 *       finalized the run (status, result, error, final context, end time).</li>
 * </ul>
 *
 * <p>Implementations must not mutate the lifecycle semantics of the run.
 */
public interface RunLifecycleExtension extends RuntimeExtension {

    default LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.BEST_EFFORT;
    }

    default void onRunStarted(ExecutionContext ctx, AssemblyRun run) {
        // no-op
    }

    default void onRunCompleted(ExecutionContext ctx, AssemblyRun run) {
        // no-op
    }
}
