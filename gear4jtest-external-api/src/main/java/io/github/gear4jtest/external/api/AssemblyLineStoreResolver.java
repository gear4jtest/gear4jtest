package io.github.gear4jtest.external.api;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.storage.ArtifactStoreProvider;

import static java.util.Objects.requireNonNull;

final class AssemblyLineStoreResolver {
    private final OperationChainConfigRepository configRepository;
    private final ArtifactStoreProvider storeProvider;
    private final Map<String, StoreCacheEntry> storeCacheByAl = new ConcurrentHashMap<>();

    AssemblyLineStoreResolver(OperationChainConfigRepository configRepository, ArtifactStoreProvider storeProvider) {
        this.configRepository = requireNonNull(configRepository);
        this.storeProvider = requireNonNull(storeProvider);
    }

    ArtifactStore resolve(String alId) {
        var config = configRepository.findByAssemblyLineId(alId)
                .orElseThrow(() -> new NoSuchElementException("Config not found for alId=" + alId));
        StoreFingerprint fingerprint = StoreFingerprint.from(config);
        StoreCacheEntry cached = storeCacheByAl.get(alId);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.store();
        }

        ArtifactStore store = storeProvider.forConfig(config);
        storeCacheByAl.put(alId, new StoreCacheEntry(fingerprint, store));
        return store;
    }

    void invalidate(String alId) {
        storeCacheByAl.remove(alId);
    }

    private record StoreCacheEntry(StoreFingerprint fingerprint, ArtifactStore store) {}

    private record StoreFingerprint(StoreType storeType, Map<String, String> storeProps) {
        private static StoreFingerprint from(OperationChainConfig config) {
            return new StoreFingerprint(config.storeType(), Map.copyOf(config.storeProps()));
        }
    }
}
