package io.github.gear4jtest.external.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.InMemoryArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssemblyLineStoreResolverTest {
    @Test
    void resolve_shouldReplaceTheCachedStoreOnlyWhenItsConfigurationChanges() {
        // Given
        OperationChainConfig firstConfig = new OperationChainConfig("line", false, StoreType.MEMORY,
                Map.of("name", "first"));
        OperationChainConfig secondConfig = new OperationChainConfig("line", false, StoreType.MEMORY,
                Map.of("name", "second"));
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId("line"))
                .thenReturn(Optional.of(firstConfig), Optional.of(secondConfig), Optional.of(secondConfig));
        AtomicInteger creations = new AtomicInteger();
        ArtifactStoreProvider storeProvider = ignored -> {
            creations.incrementAndGet();
            return new InMemoryArtifactStore();
        };
        AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(configRepository, storeProvider);

        // When
        ArtifactStore first = resolver.resolve("line");
        ArtifactStore second = resolver.resolve("line");
        ArtifactStore cachedSecond = resolver.resolve("line");

        // Then
        assertThat(second).isNotSameAs(first);
        assertThat(cachedSecond).isSameAs(second);
        assertThat(creations).hasValue(2);
    }

    @Test
    void resolve_shouldCreateOneStoreForConcurrentInitialLookups() throws Exception {
        // Given
        int concurrentLookups = 16;
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.MEMORY, Map.of());
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        CountDownLatch configLookups = new CountDownLatch(concurrentLookups);
        when(configRepository.findByAssemblyLineId("line")).thenAnswer(invocation -> {
            configLookups.countDown();
            assertThat(configLookups.await(5, TimeUnit.SECONDS)).isTrue();
            return Optional.of(config);
        });
        AtomicInteger creations = new AtomicInteger();
        ArtifactStoreProvider storeProvider = ignored -> {
            creations.incrementAndGet();
            return new InMemoryArtifactStore();
        };
        AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(configRepository, storeProvider);
        ExecutorService callers = Executors.newFixedThreadPool(concurrentLookups);
        CountDownLatch ready = new CountDownLatch(concurrentLookups);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<ArtifactStore>> results = new ArrayList<>();
            for (int index = 0; index < concurrentLookups; index++) {
                results.add(callers.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return resolver.resolve("line");
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            // When
            start.countDown();

            // Then
            ArtifactStore expected = results.get(0).get(5, TimeUnit.SECONDS);
            for (Future<ArtifactStore> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(expected);
            }
            assertThat(creations).hasValue(1);
        } finally {
            start.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void resolver_shouldBoundTheCacheAndReleaseStoresAfterTheirFinalReference() {
        // Given
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId("first"))
                .thenReturn(Optional.of(new OperationChainConfig("first", false, StoreType.MEMORY, Map.of())));
        when(configRepository.findByAssemblyLineId("second"))
                .thenReturn(Optional.of(new OperationChainConfig("second", false, StoreType.MEMORY, Map.of())));
        when(configRepository.findByAssemblyLineId("third"))
                .thenReturn(Optional.of(new OperationChainConfig("third", false, StoreType.MEMORY, Map.of("id", "3"))));
        InMemoryArtifactStore shared = new InMemoryArtifactStore();
        InMemoryArtifactStore third = new InMemoryArtifactStore();
        List<ArtifactStore> released = new ArrayList<>();
        ArtifactStoreProvider provider = new ArtifactStoreProvider() {
            @Override
            public ArtifactStore forConfig(OperationChainConfig config) {
                return "third".equals(config.alId()) ? third : shared;
            }

            @Override
            public void release(ArtifactStore store) {
                released.add(store);
            }
        };
        AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(configRepository, provider, 2);

        // When
        resolver.resolve("first");
        resolver.resolve("second");
        resolver.resolve("third");

        // Then: evicting first does not release the store still referenced by second.
        assertThat(released).isEmpty();

        // When
        resolver.invalidate("second");
        resolver.close();

        // Then
        assertThat(released).containsExactlyInAnyOrder(shared, third);
    }
}
