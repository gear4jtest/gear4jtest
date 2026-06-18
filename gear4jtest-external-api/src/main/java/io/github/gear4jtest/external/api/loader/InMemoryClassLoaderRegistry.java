package io.github.gear4jtest.external.api.loader;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bounded in-memory registry for generated classloaders.
 *
 * <p>
 * Entries are evicted with a best-effort LRU policy. Concrete loader ids that
 * are currently referenced by an alias are protected from automatic eviction so
 * a mutable alias such as {@code latest} never points to a missing loader.
 * </p>
 */
public final class InMemoryClassLoaderRegistry implements ClassLoaderRegistry {
    public static final int DEFAULT_MAX_LOADERS = 256;
    public static final int DEFAULT_MAX_PROTECTED_LOADERS = 256;

    private final int maxLoaders;
    private final int maxProtectedLoaders;
    private final LinkedHashMap<String, Holder> byId = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, String> aliasToId = new HashMap<>();
    private long evictedLoaders;

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
        this.maxLoaders = builder.maxLoaders;
        this.maxProtectedLoaders = builder.maxProtectedLoaders;
    }

    public static final class Builder {
        private int maxLoaders = DEFAULT_MAX_LOADERS;
        private int maxProtectedLoaders = DEFAULT_MAX_PROTECTED_LOADERS;

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
    public synchronized void register(String id, ClassLoader loader, GeneratedAssemblyLine bound) {
        byId.put(id, new Holder(loader, bound, Instant.now()));
        evictOverflow(id);
    }

    @Override
    public synchronized void evict(String id) {
        if (byId.remove(id) != null) {
            evictedLoaders++;
        }
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
    public synchronized GeneratedAssemblyLine getBoundAssemblyLine(String id) {
        var h = byId.get(id);
        return h == null ? null : h.chain;
    }

    public synchronized RegistryStats snapshotStats() {
        return new RegistryStats(byId.size(), aliasToId.size(), maxLoaders, evictedLoaders);
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

    private void evictOverflow(String protectedId) {
        while (byId.size() > maxLoaders) {
            String candidate = firstEvictableId(protectedId);
            if (candidate == null) {
                return;
            }
            byId.remove(candidate);
            evictedLoaders++;
        }
    }

    private String firstEvictableId(String protectedId) {
        Set<String> protectedLoaderIds = protectedLoaderIds();
        for (String id : byId.keySet()) {
            if (!id.equals(protectedId) && !protectedLoaderIds.contains(id)) {
                return id;
            }
        }
        return null;
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

    public record RegistryStats(int cachedLoaders, int aliases, int maxLoaders, long evictedLoaders) {}

    private record Holder(ClassLoader loader, GeneratedAssemblyLine chain, Instant registeredAt) {}
}
