package io.github.gear4jtest.external.api.artifact;

/**
 * Current directory-scoped spool occupancy, active JVM-local instances and
 * cumulative cleanup/quota counters.
 */
public record ArtifactSpoolStats(long currentFiles,
                                 long currentBytes,
                                 long maxBytes,
                                 long staleFilesDeleted,
                                 long staleBytesDeleted,
                                 long quotaRejections,
                                 long cleanupFailures,
                                 int activeInstances) {
    /** Compatibility constructor for snapshots without instance occupancy. */
    public ArtifactSpoolStats(long currentFiles,
                              long currentBytes,
                              long maxBytes,
                              long staleFilesDeleted,
                              long staleBytesDeleted,
                              long quotaRejections,
                              long cleanupFailures) {
        this(currentFiles, currentBytes, maxBytes, staleFilesDeleted, staleBytesDeleted,
                quotaRejections, cleanupFailures, 0);
    }
}
