package io.github.gear4jtest.external.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
    private final Map<String, PendingStore> pendingStores = new HashMap<>();
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
        OperationChainConfig config = findConfig(alId);
        synchronized (this) {
            requireOpen();
            resolutions++;
        }
        while (true) {
            StoreFingerprint fingerprint = StoreFingerprint.from(config);
            String configurationFingerprint = ArtifactStoreConfigurationFingerprint.from(config);
            PendingStore pending;
            boolean creator;
            synchronized (this) {
                requireOpen();
                StoreCacheEntry cached = storeCacheByAl.get(alId);
                if (cached != null && cached.fingerprint().equals(fingerprint)) {
                    cacheHits++;
                    cached.references++;
                    return new StoreLease(cached, configurationFingerprint);
                }
                pending = pendingStores.get(alId);
                creator = pending == null;
                if (creator) {
                    pending = new PendingStore();
                    pendingStores.put(alId, pending);
                    cacheMisses++;
                }
            }
            if (creator) {
                return createStore(alId, config, fingerprint, configurationFingerprint, pending);
            }
            pending.await();
            // Invalidation or a configuration change may have happened during construction.
            config = findConfig(alId);
        }
    }

    private OperationChainConfig findConfig(String alId) {
        return configRepository.findByAssemblyLineId(alId)
                .orElseThrow(() -> new OperationChainNotFoundException("Config not found for alId=" + alId));
    }

    private StoreLease createStore(String alId,
                                   OperationChainConfig config,
                                   StoreFingerprint fingerprint,
                                   String configurationFingerprint,
                                   PendingStore pending) {
        ArtifactStore created;
        try {
            created = requireNonNull(storeProvider.forConfig(config), "storeProvider returned null");
        } catch (RuntimeException | Error failure) {
            finishCreation(alId, pending, failure);
            throw failure;
        }
        List<ArtifactStore> retired = new ArrayList<>();
        StoreLease lease;
        synchronized (this) {
            if (closed) {
                lease = null;
            } else {
                StoreCacheEntry entry = new StoreCacheEntry(fingerprint, created);
                storeReferences.merge(created, 1, Integer::sum);
                // The initial reference belongs to this borrower until the entry is cached.
                lease = new StoreLease(entry, configurationFingerprint);
                if (!pending.invalidated) {
                    entry.references++;
                    StoreCacheEntry previous = storeCacheByAl.put(alId, entry);
                    installedEntries++;
                    if (previous != null) {
                        replacedEntries++;
                        retire(previous, retired);
                    }
                    evictEldestEntries(retired);
                }
            }
        }
        if (lease == null) {
            var failure = new IllegalStateException("Assembly-line store resolver is closed");
            try {
                releaseStores(List.of(created));
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure instanceof Error fatal) {
                    fatal.addSuppressed(failure);
                    throw fatal;
                }
                failure.addSuppressed(cleanupFailure);
            } finally {
                finishCreation(alId, pending, failure);
            }
            throw failure;
        }
        // Publish before potentially slow backend cleanup so other keys and waiters can
        // progress.
        finishCreation(alId, pending, null);
        try {
            releaseStores(retired);
            return lease;
        } catch (RuntimeException | Error failure) {
            try {
                lease.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    if (cleanupFailure instanceof Error fatal && !(failure instanceof Error)) {
                        fatal.addSuppressed(failure);
                        throw fatal;
                    }
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    private void finishCreation(String alId, PendingStore pending, Throwable failure) {
        synchronized (this) {
            pendingStores.remove(alId, pending);
        }
        if (failure == null) {
            pending.completed.complete(null);
        } else {
            pending.completed.completeExceptionally(failure);
        }
    }

    void invalidate(String alId) {
        List<ArtifactStore> retired = new ArrayList<>();
        synchronized (this) {
            PendingStore pending = pendingStores.get(alId);
            if (pending != null) {
                pending.invalidated = true;
            }
            StoreCacheEntry removed = storeCacheByAl.remove(alId);
            if (removed != null) {
                invalidatedEntries++;
                retire(removed, retired);
            }
        }
        releaseStores(retired);
    }

    synchronized ArtifactStoreResolutionStats snapshotStats() {
        return new ArtifactStoreResolutionStats(resolutions, cacheHits, cacheMisses, installedEntries,
                replacedEntries, evictedEntries, invalidatedEntries, releasedStoreLeases,
                storeCacheByAl.size(), maxCacheEntries, storeReferences.size(), closed);
    }

    @Override
    public void close() {
        List<ArtifactStore> retired = new ArrayList<>();
        List<PendingStore> pending;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            for (StoreCacheEntry entry : storeCacheByAl.values()) {
                retire(entry, retired);
            }
            storeCacheByAl.clear();
            pending = new ArrayList<>(pendingStores.values());
        }
        for (PendingStore creation : pending) {
            creation.completed
                    .completeExceptionally(new IllegalStateException("Assembly-line store resolver is closed"));
        }
        releaseStores(retired);
    }

    private void evictEldestEntries(List<ArtifactStore> retired) {
        while (storeCacheByAl.size() > maxCacheEntries) {
            var iterator = storeCacheByAl.entrySet().iterator();
            StoreCacheEntry eldest = iterator.next().getValue();
            iterator.remove();
            evictedEntries++;
            retire(eldest, retired);
        }
    }

    // Metadata only: called under this monitor. Provider callbacks run in
    // releaseStores.
    private void retire(StoreCacheEntry entry, List<ArtifactStore> retired) {
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
        retired.add(store);
    }

    private void releaseStores(List<ArtifactStore> stores) {
        Throwable failure = null;
        for (ArtifactStore store : stores) {
            try {
                storeProvider.release(store);
                synchronized (this) {
                    releasedStoreLeases++;
                }
            } catch (RuntimeException | Error cleanupFailure) {
                if (failure == null) {
                    failure = cleanupFailure;
                } else if (failure != cleanupFailure) {
                    if (cleanupFailure instanceof Error && !(failure instanceof Error)) {
                        cleanupFailure.addSuppressed(failure);
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
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

    private static final class PendingStore {
        private final Thread owner = Thread.currentThread();
        private final CompletableFuture<Void> completed = new CompletableFuture<>();
        private boolean invalidated;

        private void await() {
            if (owner == Thread.currentThread()) {
                throw new IllegalStateException("Recursive resolution of the same assembly-line store");
            }
            try {
                completed.get();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while resolving an assembly-line store", failure);
            } catch (ExecutionException failure) {
                if (failure.getCause() instanceof Error error) {
                    throw error;
                }
                if (failure.getCause() instanceof RuntimeException exception) {
                    throw exception;
                }
                throw new IllegalStateException("Assembly-line store construction failed", failure.getCause());
            }
        }
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
            List<ArtifactStore> retired = new ArrayList<>();
            synchronized (AssemblyLineStoreResolver.this) {
                if (!released) {
                    released = true;
                    retire(entry, retired);
                }
            }
            releaseStores(retired);
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
