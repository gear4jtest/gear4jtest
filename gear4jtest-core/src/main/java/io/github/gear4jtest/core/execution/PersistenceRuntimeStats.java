package io.github.gear4jtest.core.execution;

/**
 * Point-in-time observability snapshot for an asynchronous persistence
 * implementation.
 */
public record PersistenceRuntimeStats(int activeRuns,
                                      int bufferedStationLogs,
                                      long scheduledFlushes,
                                      long completedFlushes,
                                      long failedFlushes,
                                      long rejectedAppends) {}
