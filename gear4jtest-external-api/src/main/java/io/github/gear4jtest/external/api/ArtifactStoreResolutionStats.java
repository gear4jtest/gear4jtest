package io.github.gear4jtest.external.api;

/**
 * Point-in-time occupancy and cumulative lifecycle counters for the bounded
 * assembly-line store resolver.
 *
 * @param resolutions            total resolution requests
 * @param cacheHits              requests reusing an unchanged cached entry
 * @param cacheMisses            requests installing an initial or replacement
 *                               entry
 * @param installedEntries       entries installed after provider acquisition
 * @param replacedEntries        installed entries replacing changed
 *                               configuration
 * @param evictedEntries         entries removed by the capacity bound
 * @param invalidatedEntries     entries removed explicitly
 * @param releasedStoreLeases    acquired cache-entry leases returned to the
 *                               provider after cache and borrowers release them
 * @param cachedAssemblyLines    assembly-line entries currently cached
 * @param maxCachedAssemblyLines configured cache capacity
 * @param distinctStores         store identities retained by cache or
 *                               borrowers, including evicted entries still in
 *                               use
 * @param shutdown               whether the resolver is closed
 */
public record ArtifactStoreResolutionStats(long resolutions,
                                           long cacheHits,
                                           long cacheMisses,
                                           long installedEntries,
                                           long replacedEntries,
                                           long evictedEntries,
                                           long invalidatedEntries,
                                           long releasedStoreLeases,
                                           int cachedAssemblyLines,
                                           int maxCachedAssemblyLines,
                                           int distinctStores,
                                           boolean shutdown) {}
