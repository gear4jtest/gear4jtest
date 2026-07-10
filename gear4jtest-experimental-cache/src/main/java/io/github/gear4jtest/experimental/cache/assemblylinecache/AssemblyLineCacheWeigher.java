package io.github.gear4jtest.experimental.cache.assemblylinecache;

/** Estimates the relative memory weight of one cached value. */
@FunctionalInterface
public interface AssemblyLineCacheWeigher {
    /**
     * Returns a strictly positive weight for the supplied value.
     *
     * @param key    cache key
     * @param output isolated value about to be cached
     * @return value weight, greater than zero
     */
    long weigh(AssemblyLineCacheKey key, Object output);

    /** Returns a weigher that counts every entry as one unit. */
    static AssemblyLineCacheWeigher entryCount() {
        return (key, output) -> 1L;
    }
}
