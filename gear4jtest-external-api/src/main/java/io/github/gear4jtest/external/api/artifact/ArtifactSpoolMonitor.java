package io.github.gear4jtest.external.api.artifact;

/** Exposes private artifact-spool occupancy, cleanup and quota state. */
public interface ArtifactSpoolMonitor {
    ArtifactSpoolStats snapshotSpoolStats();
}
