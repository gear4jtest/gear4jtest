package io.github.gear4jtest.external.api.artifact;

/** Exposes cumulative operational counters for an artifact store. */
public interface ArtifactStoreMonitor {
    ArtifactStoreStats snapshotStats();
}
