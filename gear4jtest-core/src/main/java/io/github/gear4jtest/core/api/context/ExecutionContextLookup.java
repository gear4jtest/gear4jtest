package io.github.gear4jtest.core.api.context;

import java.util.UUID;

/**
 * Read-only lookup used by runtime features that need an active run context.
 */
@FunctionalInterface
public interface ExecutionContextLookup {
    ExecutionContext find(UUID executionId);
}
