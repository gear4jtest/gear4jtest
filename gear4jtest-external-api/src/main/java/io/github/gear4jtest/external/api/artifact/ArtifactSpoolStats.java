package io.github.gear4jtest.external.api.artifact;

/** Current spool occupancy and cumulative cleanup/quota counters. */
public record ArtifactSpoolStats(long currentFiles,
                                 long currentBytes,
                                 long maxBytes,
                                 long staleFilesDeleted,
                                 long staleBytesDeleted,
                                 long quotaRejections,
                                 long cleanupFailures) {}
