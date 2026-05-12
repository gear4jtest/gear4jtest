package io.github.gear4jtest.core.execution;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.gear4jtest.core.api.context.ExecutionContext;

/**
 * Thread-safe registry mapping execution identifiers to their active
 * {@link ExecutionContext}.
 *
 * <p>
 * Contexts are registered when a pipeline starts and removed when the execution
 * completes.
 * </p>
 */
public final class ExecutionContextRegistry {

    private final ConcurrentMap<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();

    /**
     * Registers the provided context when it has an execution id.
     */
    public void register(ExecutionContext ctx) {
        if (ctx == null || ctx.getExecutionId() == null) {
            return;
        }
        contexts.put(ctx.getExecutionId(), ctx);
    }

    /**
     * Returns the context associated with the execution id, or {@code null} when
     * none is registered.
     */
    public ExecutionContext get(UUID executionId) {
        if (executionId == null) {
            return null;
        }
        return contexts.get(executionId);
    }

    /**
     * Removes the context associated with the execution id.
     */
    public void remove(UUID executionId) {
        if (executionId == null) {
            return;
        }
        contexts.remove(executionId);
    }
}
