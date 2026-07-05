package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAssemblyLineCacheRepository implements AssemblyLineCacheRepository {
    private final Map<AssemblyLineCacheKey, AssemblyLineCacheEntry<?>> entries = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <OUT> Optional<AssemblyLineCacheEntry<OUT>> findValid(AssemblyLineCacheKey key, Instant now) {
        AssemblyLineCacheEntry<?> entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.isValidAt(now)) {
            return Optional.empty();
        }
        return Optional.of((AssemblyLineCacheEntry<OUT>) entry);
    }

    @Override
    public <OUT> void save(AssemblyLineCacheEntry<OUT> entry) {
        entries.put(entry.key(), entry);
    }
}
