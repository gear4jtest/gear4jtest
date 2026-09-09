package io.github.gear4jtest.external.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.storage.DefaultArtifactStoreProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssemblyLineStoreLeaseTest {
    @Test
    void close_shouldReturnEveryLeaseToTheRealDefaultProvider() {
        // Given
        OperationChainConfigRepository repository = configurations();
        try (DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                getClass().getClassLoader(), null, Runnable::run)) {
            ArtifactStore shared;
            try (AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(repository, provider);
                    var first = resolver.acquire("first");
                    var second = resolver.acquire("second")) {
                shared = first.store();
                assertThat(second.store()).isSameAs(shared);
            }

            // When: a fresh acquisition must not reuse the formerly leased backend.
            ArtifactStore next = provider.forConfig(config("first"));
            try {
                // Then
                assertThat(next).isNotSameAs(shared);
            } finally {
                provider.release(next);
            }
        }
    }

    @Test
    void acquire_shouldKeepAStreamUsableDuringEvictionByAnotherThread() throws Exception {
        // Given
        TrackingStore first = new TrackingStore();
        TrackingStore second = new TrackingStore();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try (AssemblyLineStoreResolver resolver = resolver(first, second);
                var lease = resolver.acquire("first");
                InputStream stream = lease.store().get(first.hash).orElseThrow().openStreamChecked()) {
            // When
            caller.submit(() -> {
                try (var other = resolver.acquire("second")) {
                    return other.store();
                }
            }).get(5, TimeUnit.SECONDS);

            // Then
            assertThat(stream.readAllBytes()).isEqualTo(first.bytes);
            assertThat(first.closes).hasValue(0);
            assertThat(resolver.snapshotStats().distinctStores()).isEqualTo(2);
        } finally {
            caller.shutdownNow();
            assertThat(caller.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(first.closes).hasValue(1);
        assertThat(first.streamCloses).hasValue(1);
        assertThat(second.closes).hasValue(1);
    }

    @Test
    void invalidate_shouldWaitForTheFinalBorrowerAndCloseEachLeaseOnlyOnce() throws Exception {
        // Given
        TrackingStore store = new TrackingStore();
        try (AssemblyLineStoreResolver resolver = resolver(store, new TrackingStore());
                var first = resolver.acquire("first");
                var second = resolver.acquire("first")) {
            // When
            resolver.invalidate("first");
            first.close();
            first.close();

            // Then
            assertThat(store.closes).hasValue(0);
            assertThat(second.store().put(store.bytes)).isEqualTo(store.hash);
            assertThatThrownBy(first::store).isInstanceOf(IllegalStateException.class);
        }
        assertThat(store.closes).hasValue(1);
    }

    @Test
    void close_shouldKeepActiveBorrowersAliveAndRejectNewAcquisitions() throws Exception {
        // Given
        TrackingStore store = new TrackingStore();
        try (AssemblyLineStoreResolver resolver = resolver(store, new TrackingStore());
                var lease = resolver.acquire("first")) {
            // When
            resolver.close();
            resolver.close();

            // Then
            assertThat(store.closes).hasValue(0);
            assertThat(lease.store().put(store.bytes)).isEqualTo(store.hash);
            assertThatThrownBy(() -> resolver.acquire("first")).isInstanceOf(IllegalStateException.class);
        }
        assertThat(store.closes).hasValue(1);
    }

    @Test
    void acquire_shouldKeepReplacedEntriesAliveUntilTheirBorrowersFinish() throws Exception {
        // Given
        OperationChainConfigRepository repository = configurations();
        OperationChainConfig changed = new OperationChainConfig("first", false, StoreType.MEMORY,
                Map.of("maxEntries", "10"));
        when(repository.findByAssemblyLineId("first"))
                .thenReturn(Optional.of(config("first")), Optional.of(changed));
        TrackingStore oldStore = new TrackingStore();
        TrackingStore newStore = new TrackingStore();
        try (AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(repository,
                provider(oldStore, newStore, true));
                var oldLease = resolver.acquire("first")) {
            // When
            try (var newLease = resolver.acquire("first")) {
                // Then
                assertThat(newLease.store()).isSameAs(newStore);
                assertThat(oldLease.store().put(oldStore.bytes)).isEqualTo(oldStore.hash);
                assertThat(oldStore.closes).hasValue(0);
            }
        }
        assertThat(oldStore.closes).hasValue(1);
        assertThat(newStore.closes).hasValue(1);
    }

    @Test
    void acquire_shouldBalanceReplacementEvenWhenTheProviderReturnsTheSameInstance() {
        // Given
        OperationChainConfigRepository repository = configurations();
        when(repository.findByAssemblyLineId("first")).thenReturn(Optional.of(config("first")),
                                                                  Optional.of(new OperationChainConfig("first", false,
                                                                          StoreType.MEMORY,
                                                                          Map.of("maxEntries", "10"))));
        TrackingStore shared = new TrackingStore();
        AtomicInteger acquisitions = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        ArtifactStoreProvider provider = new ArtifactStoreProvider() {
            @Override
            public ArtifactStore forConfig(OperationChainConfig config) {
                acquisitions.incrementAndGet();
                return shared;
            }

            @Override
            public void release(ArtifactStore store) {
                releases.incrementAndGet();
            }
        };
        try (AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(repository, provider);
                var first = resolver.acquire("first");
                var replacement = resolver.acquire("first")) {
            assertThat(first.store()).isSameAs(replacement.store());
            assertThat(releases).hasValue(0);
        }
        // Then: each configuration acquisition is returned despite identical stores.
        assertThat(acquisitions).hasValue(2);
        assertThat(releases).hasValue(2);
    }

    @Test
    void close_shouldPreserveTheOperationFailureWhenLeaseCleanupAlsoFails() {
        // Given
        TrackingStore store = new TrackingStore();
        IllegalStateException cleanup = new IllegalStateException("release failed");
        store.closeFailure = cleanup;
        IOException original = new IOException("write failed");
        try (AssemblyLineStoreResolver resolver = resolver(store, new TrackingStore())) {
            // When
            Throwable failure = catchThrowable(() -> {
                try (var lease = resolver.acquire("first")) {
                    resolver.invalidate("first");
                    assertThat(lease.store()).isSameAs(store);
                    throw original;
                }
            });

            // Then
            assertThat(failure).isSameAs(original);
            assertThat(failure.getSuppressed()).containsExactly(cleanup);
            assertThat(store.closes).hasValue(1);
        }
    }

    @Test
    void close_shouldAttemptEveryCachedReleaseEvenWhenCleanupFails() {
        // Given
        TrackingStore first = new TrackingStore();
        TrackingStore second = new TrackingStore();
        first.closeFailure = new IllegalStateException("first release failed");
        second.closeFailure = new IllegalStateException("second release failed");
        AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(configurations(),
                provider(first, second, false), 2);
        try (var firstLease = resolver.acquire("first");
                var secondLease = resolver.acquire("second")) {
            assertThat(firstLease.store()).isSameAs(first);
            assertThat(secondLease.store()).isSameAs(second);
        }

        // When
        Throwable failure = catchThrowable(resolver::close);
        resolver.close();

        // Then
        assertThat(failure).isSameAs(first.closeFailure);
        assertThat(failure.getSuppressed()).containsExactly(second.closeFailure);
        assertThat(first.closes).hasValue(1);
        assertThat(second.closes).hasValue(1);
    }

    @Test
    void close_shouldRespectCallerOwnershipWithTheDefaultProviderRelease() {
        // Given
        TrackingStore store = new TrackingStore();
        try (AssemblyLineStoreResolver resolver = new AssemblyLineStoreResolver(configurations(), ignored -> store);
                var lease = resolver.acquire("first")) {
            // When
            resolver.invalidate("first");
            assertThat(lease.store()).isSameAs(store);
        }
        // Then
        assertThat(store.closes).hasValue(0);
    }

    static AssemblyLineStoreResolver resolver(TrackingStore first, TrackingStore second) {
        return new AssemblyLineStoreResolver(configurations(), provider(first, second, false), 1);
    }

    static OperationChainConfigRepository configurations() {
        OperationChainConfigRepository repository = mock(OperationChainConfigRepository.class);
        when(repository.findByAssemblyLineId("first")).thenReturn(Optional.of(config("first")));
        when(repository.findByAssemblyLineId("second")).thenReturn(Optional.of(config("second")));
        return repository;
    }

    private static OperationChainConfig config(String id) {
        return new OperationChainConfig(id, false, StoreType.MEMORY, Map.of());
    }

    private static ArtifactStoreProvider provider(TrackingStore first, TrackingStore second, boolean replacement) {
        return new ArtifactStoreProvider() {
            @Override
            public ArtifactStore forConfig(OperationChainConfig config) {
                return (replacement ? config.storeProps().isEmpty() : "first".equals(config.alId())) ? first : second;
            }

            @Override
            public void release(ArtifactStore store) {
                store.close();
            }
        };
    }

    static final class TrackingStore implements ArtifactStore {
        final byte[] bytes = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        final String hash = ArtifactHashes.sha256Hex(bytes);
        final AtomicInteger closes = new AtomicInteger();
        final AtomicInteger streamCloses = new AtomicInteger();
        final List<String> lifecycle = new ArrayList<>();
        Runnable onRead = () -> {
        };
        IOException readFailure;
        RuntimeException closeFailure;

        @Override
        public String put(byte[] content) throws IOException {
            requireOpen();
            return ArtifactHashes.sha256Hex(content);
        }

        @Override
        public Optional<Artifact> get(String requestedHash) throws IOException {
            requireOpen();
            if (!hash.equals(requestedHash)) {
                return Optional.empty();
            }
            return Optional.of(Artifact.streaming(hash, bytes.length, Map.of(), () -> new InputStream() {
                private final InputStream delegate = new ByteArrayInputStream(bytes);

                @Override
                public int read() throws IOException {
                    onRead.run();
                    requireOpen();
                    if (readFailure != null) {
                        throw readFailure;
                    }
                    return delegate.read();
                }

                @Override
                public void close() throws IOException {
                    delegate.close();
                    streamCloses.incrementAndGet();
                    lifecycle.add("stream closed");
                }
            }));
        }

        @Override
        public boolean exists(String requestedHash) throws IOException {
            requireOpen();
            return hash.equals(requestedHash);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
            lifecycle.add("store closed");
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

        private void requireOpen() throws IOException {
            if (closes.get() != 0) {
                throw new IOException("store closed during use");
            }
        }
    }
}
