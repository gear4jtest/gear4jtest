package io.github.gear4jtest.core.execution;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionContextLookup;

/**
 * Thread-safe registry mapping execution identifiers to their active
 * {@link ExecutionContext}.
 *
 * <p>
 * Contexts are registered when a pipeline starts and removed when the execution
 * completes.
 * </p>
 */
public final class ExecutionContextRegistry implements ExecutionContextLookup {
    private final ConcurrentMap<UUID, ExecutionContext> contexts = new ConcurrentHashMap<>();

    /**
     * Registers the provided context when it has an execution id.
     *
     * @throws IllegalStateException when another active context already uses the
     *                               same execution id
     */
    public void register(ExecutionContext ctx) {
        if (ctx == null) {
            return;
        }
        UUID executionId = ctx.getExecutionId();
        if (executionId == null) {
            return;
        }
        ExecutionContext previous = contexts.putIfAbsent(executionId, ctx);
        if (previous != null && previous != ctx) {
            throw new IllegalStateException("Duplicate active execution id: " + executionId);
        }
    }

    /**
     * Returns the context associated with the execution id, or {@code null} when
     * none is registered.
     */
    @Override
    public ExecutionContext find(UUID executionId) {
        if (executionId == null) {
            return null;
        }
        return contexts.get(executionId);
    }

    /** @deprecated Use {@link #find(UUID)}. */
    @Deprecated(forRemoval = true)
    public ExecutionContext get(UUID executionId) {
        return find(executionId);
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

    /**
     * Removes the context only when the registry still points to the expected
     * instance.
     */
    public void remove(UUID executionId, ExecutionContext expectedContext) {
        if (executionId == null || expectedContext == null) {
            return;
        }
        contexts.remove(executionId, expectedContext);
    }
}
