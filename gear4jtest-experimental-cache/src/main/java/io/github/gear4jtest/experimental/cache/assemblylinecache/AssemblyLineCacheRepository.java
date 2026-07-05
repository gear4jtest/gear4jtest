package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.time.Instant;
import java.util.Optional;

public interface AssemblyLineCacheRepository {
    <OUT> Optional<AssemblyLineCacheEntry<OUT>> findValid(AssemblyLineCacheKey key, Instant now);

    <OUT> void save(AssemblyLineCacheEntry<OUT> entry);
}
