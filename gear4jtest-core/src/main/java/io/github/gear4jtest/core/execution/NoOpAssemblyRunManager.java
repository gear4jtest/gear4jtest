package io.github.gear4jtest.core.execution;

import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;

public class NoOpAssemblyRunManager implements RunPersistenceManager {
    public static final RunPersistenceManager NO_OP_INSTANCE = new NoOpAssemblyRunManager();

    public NoOpAssemblyRunManager() {
        // Public constructor allows tests and lightweight integrations to opt out
        // explicitly.
    }

    @Override
    public void start(RunTrace execution) {
        // No op
    }

    @Override
    public void end(RunTrace finalExecution) {
        // No op
    }
}
