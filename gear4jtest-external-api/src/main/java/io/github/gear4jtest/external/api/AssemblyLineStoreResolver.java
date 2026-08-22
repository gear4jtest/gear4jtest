package io.github.gear4jtest.external.api;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.storage.ArtifactStoreConfigurationFingerprint;

import static java.util.Objects.requireNonNull;

final class AssemblyLineStoreResolver implements AutoCloseable {
    static final int DEFAULT_MAX_CACHE_ENTRIES = 256;

    private final OperationChainConfigRepository configRepository;
    private final ArtifactStoreProvider storeProvider;
    private final int maxCacheEntries;
    private final Map<String, StoreCacheEntry> storeCacheByAl = new LinkedHashMap<>(16, 0.75f, true);
    private final IdentityHashMap<ArtifactStore, Integer> storeReferences = new IdentityHashMap<>();
    private boolean closed;

    AssemblyLineStoreResolver(OperationChainConfigRepository configRepository, ArtifactStoreProvider storeProvider) {
        this(configRepository, storeProvider, DEFAULT_MAX_CACHE_ENTRIES);
    }

    AssemblyLineStoreResolver(OperationChainConfigRepository configRepository,
                              ArtifactStoreProvider storeProvider,
                              int maxCacheEntries) {
        this.configRepository = requireNonNull(configRepository);
        this.storeProvider = requireNonNull(storeProvider);
        if (maxCacheEntries <= 0) {
            throw new IllegalArgumentException("maxCacheEntries must be > 0");
        }
        this.maxCacheEntries = maxCacheEntries;
    }

    ArtifactStore resolve(String alId) {
        return resolveForPublication(alId).store();
    }

    ResolvedStore resolveForPublication(String alId) {
        var config = configRepository.findByAssemblyLineId(alId)
                .orElseThrow(() -> new OperationChainNotFoundException("Config not found for alId=" + alId));
        StoreFingerprint fingerprint = StoreFingerprint.from(config);
        synchronized (this) {
            requireOpen();
            StoreCacheEntry resolved = storeCacheByAl.get(alId);
            if (resolved == null || !resolved.fingerprint().equals(fingerprint)) {
                ArtifactStore replacement = requireNonNull(storeProvider.forConfig(config),
                                                           "storeProvider returned null");
                StoreCacheEntry previous = storeCacheByAl.put(alId, new StoreCacheEntry(fingerprint, replacement));
                if (previous == null || previous.store() != replacement) {
                    retain(replacement);
                    release(previous);
                }
                resolved = storeCacheByAl.get(alId);
                evictEldestEntries();
            }
            return new ResolvedStore(resolved.store(), ArtifactStoreConfigurationFingerprint.from(config));
        }
    }

    synchronized void invalidate(String alId) {
        release(storeCacheByAl.remove(alId));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<StoreCacheEntry> entries = new ArrayList<>(storeCacheByAl.values());
        storeCacheByAl.clear();
        for (StoreCacheEntry entry : entries) {
            release(entry);
        }
    }

    private void evictEldestEntries() {
        while (storeCacheByAl.size() > maxCacheEntries) {
            var iterator = storeCacheByAl.entrySet().iterator();
            StoreCacheEntry eldest = iterator.next().getValue();
            iterator.remove();
            release(eldest);
        }
    }

    private void retain(ArtifactStore store) {
        storeReferences.merge(store, 1, Integer::sum);
    }

    private void release(StoreCacheEntry entry) {
        if (entry == null) {
            return;
        }
        ArtifactStore store = entry.store();
        Integer references = storeReferences.get(store);
        if (references == null || references <= 1) {
            storeReferences.remove(store);
            storeProvider.release(store);
        } else {
            storeReferences.put(store, references - 1);
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Assembly-line store resolver is closed");
        }
    }

    record ResolvedStore(ArtifactStore store, String configurationFingerprint) {}

    private record StoreCacheEntry(StoreFingerprint fingerprint, ArtifactStore store) {}

    private record StoreFingerprint(StoreType storeType, Map<String, String> storeProps) {
        private static StoreFingerprint from(OperationChainConfig config) {
            return new StoreFingerprint(config.storeType(), Map.copyOf(config.storeProps()));
        }
    }
}
