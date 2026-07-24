package io.github.gear4jtest.external.api.artifact;

/** Cumulative size, latency and failure counters for one artifact store. */
public record ArtifactStoreStats(long writesCompleted,
                                 long writeFailures,
                                 long bytesWritten,
                                 long writeDurationNanos,
                                 long readStreamsOpened,
                                 long readStreamsCompleted,
                                 long readStreamsClosedEarly,
                                 long readFailures,
                                 long bytesRead,
                                 long readDurationNanos,
                                 long cleanupFailures) {}
