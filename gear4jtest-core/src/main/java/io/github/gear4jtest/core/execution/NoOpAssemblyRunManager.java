package io.github.gear4jtest.core.execution;

import io.github.gear4jtest.core.persistence.AssemblyRun;

public class NoOpAssemblyRunManager implements AssemblyRunManager {

    public static final AssemblyRunManager NO_OP_INSTANCE = new NoOpAssemblyRunManager();

    public NoOpAssemblyRunManager() {
    }

    @Override
    public void start(AssemblyRun execution) {
        // No op
    }

    @Override
    public void end(AssemblyRun finalExecution) {
        // No op
    }
}
