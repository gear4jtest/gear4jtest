package io.github.gear4jtest.external.api.loader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded in-memory registry for generated classloaders.
 *
 * <p>
 * Entries are evicted with a best-effort count-based and hard weight-based LRU
 * policy. Concrete loader ids that are currently referenced by an alias are
 * protected from automatic eviction so a mutable alias such as {@code latest}
 * never points to a missing loader.
 * </p>
 */
public final class InMemoryClassLoaderRegistry implements ClassLoaderRegistry {
    public static final int DEFAULT_MAX_LOADERS = 256;
    public static final int DEFAULT_MAX_PROTECTED_LOADERS = 256;
    public static final long DEFAULT_MAX_BYTECODE_WEIGHT_BYTES = 64L * 1024L * 1024L;

    private final int maxLoaders;
    private final int maxProtectedLoaders;
    private final long maxBytecodeWeightBytes;
    private final LinkedHashMap<String, Holder> byId = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, String> aliasToId = new HashMap<>();
    private long evictedLoaders;
    private long rejectedLoaders;
    private long bytecodeWeightBytes;

    public static Builder builder() {
        return new Builder();
    }

    private InMemoryClassLoaderRegistry(Builder builder) {
        if (builder.maxLoaders < 1) {
            throw new IllegalArgumentException("maxLoaders must be >= 1");
        }
        if (builder.maxProtectedLoaders < 1) {
            throw new IllegalArgumentException("maxProtectedLoaders must be >= 1");
        }
        if (builder.maxBytecodeWeightBytes < 1L) {
            throw new IllegalArgumentException("maxBytecodeWeightBytes must be >= 1");
        }
        this.maxLoaders = builder.maxLoaders;
        this.maxProtectedLoaders = builder.maxProtectedLoaders;
        this.maxBytecodeWeightBytes = builder.maxBytecodeWeightBytes;
    }

    public static final class Builder {
        private int maxLoaders = DEFAULT_MAX_LOADERS;
        private int maxProtectedLoaders = DEFAULT_MAX_PROTECTED_LOADERS;
        private long maxBytecodeWeightBytes = DEFAULT_MAX_BYTECODE_WEIGHT_BYTES;

        private Builder() {
        }

        public Builder maxLoaders(int maxLoaders) {
            this.maxLoaders = maxLoaders;
            return this;
        }

        public Builder maxProtectedLoaders(int maxProtectedLoaders) {
            this.maxProtectedLoaders = maxProtectedLoaders;
            return this;
        }

        public Builder maxBytecodeWeightBytes(long maxBytecodeWeightBytes) {
            this.maxBytecodeWeightBytes = maxBytecodeWeightBytes;
            return this;
        }

        public InMemoryClassLoaderRegistry build() {
            return new InMemoryClassLoaderRegistry(this);
        }
    }

    @Override
    public synchronized ClassLoader get(String id) {
        var h = byId.get(id);
        return h == null ? null : h.loader;
    }

    @Override
    public synchronized void register(String id,
                                      ClassLoader loader,
                                      GeneratedAssemblyLine<?, ?> bound,
                                      long loaderBytecodeWeightBytes) {
        if (loaderBytecodeWeightBytes < 0L) {
            throw new IllegalArgumentException("bytecodeWeightBytes must be >= 0");
        }
        if (loaderBytecodeWeightBytes > maxBytecodeWeightBytes) {
            rejectedLoaders++;
            throw bytecodeLimitExceeded(loaderBytecodeWeightBytes);
        }

        Holder previous = byId.get(id);
        long replacedWeight = previous == null ? 0L : previous.bytecodeWeightBytes;
        long projectedWeight = bytecodeWeightBytes - replacedWeight + loaderBytecodeWeightBytes;
        int projectedCount = byId.size() - (previous == null ? 0 : 1) + 1;
        List<String> evictions = new ArrayList<>();

        while (projectedCount > maxLoaders || projectedWeight > maxBytecodeWeightBytes) {
            String candidate = firstEvictableId(id, Set.copyOf(evictions));
            if (candidate == null) {
                if (projectedWeight > maxBytecodeWeightBytes) {
                    rejectedLoaders++;
                    throw bytecodeLimitExceeded(projectedWeight);
                }
                break;
            }
            Holder evicted = byId.get(candidate);
            evictions.add(candidate);
            projectedCount--;
            projectedWeight -= evicted.bytecodeWeightBytes;
        }

        evictions.forEach(this::removeLoader);
        Holder replaced = byId.put(id, new Holder(loader, bound, Instant.now(), loaderBytecodeWeightBytes));
        if (replaced != null) {
            bytecodeWeightBytes -= replaced.bytecodeWeightBytes;
        }
        bytecodeWeightBytes += loaderBytecodeWeightBytes;
    }

    @Override
    public synchronized void evict(String id) {
        removeLoader(id);
        aliasToId.values().removeIf(v -> v.equals(id));
    }

    @Override
    public synchronized void setAlias(String alias, String id) {
        if (id == null) {
            aliasToId.remove(alias);
        } else if (byId.containsKey(id)) {
            requireProtectedLoaderCapacity(alias, id);
            aliasToId.put(alias, id);
        } else {
            throw new IllegalArgumentException("Cannot alias missing classloader id: " + id);
        }
    }

    @Override
    public synchronized void clearAlias(String alias) {
        aliasToId.remove(alias);
    }

    @Override
    public synchronized String resolveAlias(String alias) {
        return aliasToId.get(alias);
    }

    @Override
    public synchronized GeneratedAssemblyLine<?, ?> getBoundAssemblyLine(String id) {
        var h = byId.get(id);
        return h == null ? null : h.chain;
    }

    public synchronized RegistryStats snapshotStats() {
        return new RegistryStats(byId.size(), aliasToId.size(), maxLoaders, evictedLoaders, rejectedLoaders,
                bytecodeWeightBytes, maxBytecodeWeightBytes);
    }

    public synchronized int protectedLoaderCount() {
        return protectedLoaderIds().size();
    }

    public synchronized int maxProtectedLoaders() {
        return maxProtectedLoaders;
    }

    public synchronized boolean isOverCapacityDueToProtectedLoaders() {
        return byId.size() > maxLoaders && protectedLoaderCount() > 0;
    }

    private String firstEvictableId(String protectedId, Set<String> plannedEvictions) {
        Set<String> protectedLoaderIds = protectedLoaderIds();
        for (String id : byId.keySet()) {
            if (!id.equals(protectedId) && !protectedLoaderIds.contains(id) && !plannedEvictions.contains(id)) {
                return id;
            }
        }
        return null;
    }

    private void removeLoader(String id) {
        Holder removed = byId.remove(id);
        if (removed != null) {
            bytecodeWeightBytes -= removed.bytecodeWeightBytes;
            evictedLoaders++;
        }
    }

    private IllegalStateException bytecodeLimitExceeded(long projectedWeight) {
        return new IllegalStateException("Generated classloader bytecode weight exceeds hard limit: "
                + projectedWeight + " bytes > " + maxBytecodeWeightBytes + " bytes");
    }

    private void requireProtectedLoaderCapacity(String alias, String id) {
        String previousId = aliasToId.get(alias);
        if (id.equals(previousId)) {
            return;
        }
        Set<String> protectedLoaderIds = protectedLoaderIds();
        protectedLoaderIds.remove(previousId);
        protectedLoaderIds.add(id);
        if (protectedLoaderIds.size() > maxProtectedLoaders) {
            throw new IllegalStateException("Cannot protect more than " + maxProtectedLoaders
                    + " generated classloaders with aliases");
        }
    }

    private Set<String> protectedLoaderIds() {
        return new HashSet<>(aliasToId.values());
    }

    public record RegistryStats(int cachedLoaders,
                                int aliases,
                                int maxLoaders,
                                long evictedLoaders,
                                long rejectedLoaders,
                                long bytecodeWeightBytes,
                                long maxBytecodeWeightBytes) {}

    private record Holder(ClassLoader loader,
                          GeneratedAssemblyLine<?, ?> chain,
                          Instant registeredAt,
                          long bytecodeWeightBytes) {}
}
