package io.github.gear4jtest.experimental.cache.assemblylinecache;

/**
 * Point-in-time statistics for an in-memory assembly-line cache.
 *
 * @param hits                 valid entries returned to callers
 * @param misses               absent or expired lookups
 * @param writes               entries accepted by the cache
 * @param expiredEvictions     entries removed after their TTL elapsed
 * @param capacityEvictions    entries removed to enforce size or weight limits
 * @param rejectedWrites       entries rejected because isolation or weight
 *                             validation failed
 * @param entryCount           entries currently retained
 * @param estimatedWeight      current weight reported by the configured weigher
 * @param loadCount            cache-miss computations recorded by the extension
 * @param totalLoadTimeNanos   cumulative cache-miss computation time
 * @param maximumLoadTimeNanos longest recorded cache-miss computation time
 */
public record AssemblyLineCacheStats(long hits,
                                     long misses,
                                     long writes,
                                     long expiredEvictions,
                                     long capacityEvictions,
                                     long rejectedWrites,
                                     int entryCount,
                                     long estimatedWeight,
                                     long loadCount,
                                     long totalLoadTimeNanos,
                                     long maximumLoadTimeNanos) {}
