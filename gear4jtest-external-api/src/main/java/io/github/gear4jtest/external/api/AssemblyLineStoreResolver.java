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
    private long resolutions;
    private long cacheHits;
    private long cacheMisses;
    private long installedEntries;
    private long replacedEntries;
    private long evictedEntries;
    private long invalidatedEntries;
    private long releasedStoreLeases;
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

    StoreLease acquire(String alId) {
        var config = configRepository.findByAssemblyLineId(alId)
                .orElseThrow(() -> new OperationChainNotFoundException("Config not found for alId=" + alId));
        StoreFingerprint fingerprint = StoreFingerprint.from(config);
        String configurationFingerprint = ArtifactStoreConfigurationFingerprint.from(config);
        synchronized (this) {
            requireOpen();
            resolutions++;
            StoreCacheEntry resolved = storeCacheByAl.get(alId);
            if (resolved == null || !resolved.fingerprint().equals(fingerprint)) {
                cacheMisses++;
                ArtifactStore replacement = requireNonNull(storeProvider.forConfig(config),
                                                           "storeProvider returned null");
                StoreCacheEntry previous = storeCacheByAl.put(alId, new StoreCacheEntry(fingerprint, replacement));
                retain(replacement);
                installedEntries++;
                if (previous != null) {
                    replacedEntries++;
                }
                release(previous);
                resolved = storeCacheByAl.get(alId);
                evictEldestEntries();
            } else {
                cacheHits++;
            }
            StoreLease lease = new StoreLease(resolved, configurationFingerprint);
            resolved.references++;
            return lease;
        }
    }

    synchronized void invalidate(String alId) {
        StoreCacheEntry removed = storeCacheByAl.remove(alId);
        if (removed != null) {
            invalidatedEntries++;
            release(removed);
        }
    }

    synchronized ArtifactStoreResolutionStats snapshotStats() {
        return new ArtifactStoreResolutionStats(resolutions, cacheHits, cacheMisses, installedEntries,
                replacedEntries, evictedEntries, invalidatedEntries, releasedStoreLeases,
                storeCacheByAl.size(), maxCacheEntries, storeReferences.size(), closed);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<StoreCacheEntry> entries = new ArrayList<>(storeCacheByAl.values());
        storeCacheByAl.clear();
        Throwable failure = null;
        for (StoreCacheEntry entry : entries) {
            try {
                release(entry);
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) {
                    failure = cleanupFailure;
                } else if (failure != cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
    }

    private void evictEldestEntries() {
        while (storeCacheByAl.size() > maxCacheEntries) {
            var iterator = storeCacheByAl.entrySet().iterator();
            StoreCacheEntry eldest = iterator.next().getValue();
            iterator.remove();
            evictedEntries++;
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
        if (--entry.references > 0) {
            return;
        }
        ArtifactStore store = entry.store();
        Integer references = storeReferences.get(store);
        if (references == null || references <= 1) {
            storeReferences.remove(store);
        } else {
            storeReferences.put(store, references - 1);
        }
        storeProvider.release(store);
        releasedStoreLeases++;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Assembly-line store resolver is closed");
        }
    }

    /**
     * Keeps an entry's provider lease alive through one complete operation,
     * including any streams it opens. Closing the resolver only removes cache
     * ownership; a borrower returns the provider lease after its final use.
     */
    final class StoreLease implements AutoCloseable {
        private final StoreCacheEntry entry;
        private final String configurationFingerprint;
        private boolean released;

        private StoreLease(StoreCacheEntry entry, String configurationFingerprint) {
            this.entry = entry;
            this.configurationFingerprint = configurationFingerprint;
        }

        ArtifactStore store() {
            synchronized (AssemblyLineStoreResolver.this) {
                if (released) {
                    throw new IllegalStateException("Artifact-store lease is closed");
                }
                return entry.store();
            }
        }

        String configurationFingerprint() {
            return configurationFingerprint;
        }

        @Override
        public void close() {
            synchronized (AssemblyLineStoreResolver.this) {
                if (!released) {
                    released = true;
                    release(entry);
                }
            }
        }
    }

    private static final class StoreCacheEntry {
        private final StoreFingerprint fingerprint;
        private final ArtifactStore store;
        private int references = 1;

        private StoreCacheEntry(StoreFingerprint fingerprint, ArtifactStore store) {
            this.fingerprint = fingerprint;
            this.store = store;
        }

        private StoreFingerprint fingerprint() {
            return fingerprint;
        }

        private ArtifactStore store() {
            return store;
        }
    }

    private record StoreFingerprint(StoreType storeType, Map<String, String> storeProps) {
        private static StoreFingerprint from(OperationChainConfig config) {
            return new StoreFingerprint(config.storeType(), Map.copyOf(config.storeProps()));
        }
    }
}
