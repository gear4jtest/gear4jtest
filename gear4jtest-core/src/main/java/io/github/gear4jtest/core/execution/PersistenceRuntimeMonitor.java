package io.github.gear4jtest.core.execution;

/** Exposes runtime statistics for a persistence implementation. */
public interface PersistenceRuntimeMonitor {
    PersistenceRuntimeStats snapshotStats();
}
