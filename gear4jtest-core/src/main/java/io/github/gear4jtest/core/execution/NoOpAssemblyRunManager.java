package io.github.gear4jtest.core.execution;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;

public class NoOpAssemblyRunManager implements AssemblyRunManager {
    public static final AssemblyRunManager NO_OP_INSTANCE = new NoOpAssemblyRunManager();

    public NoOpAssemblyRunManager() {
        // Public constructor allows tests and lightweight integrations to opt out
        // explicitly.
    }

    @Override
    public void start(AssemblyRunTrace execution) {
        // No op
    }

    @Override
    public void end(AssemblyRunTrace finalExecution) {
        // No op
    }
}
