package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.time.Duration;

/**
 * Optional statistics contract implemented by observable cache repositories.
 */
public interface AssemblyLineCacheMetrics {
    /** Records the duration of one cache-miss computation. */
    void recordLoadDuration(Duration duration);

    /** Returns a point-in-time statistics snapshot. */
    AssemblyLineCacheStats snapshotStats();
}
