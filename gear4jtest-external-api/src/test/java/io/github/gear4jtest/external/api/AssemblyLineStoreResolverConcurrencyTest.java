package io.github.gear4jtest.external.api;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssemblyLineStoreResolverConcurrencyTest {
    private final ExecutorService callers = Executors.newFixedThreadPool(4);

    @AfterEach
    void stopCallers() throws Exception {
        callers.shutdownNow();
        assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void slowConstruction_shouldNotBlockAnIndependentCacheHitOrStats() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        ArtifactStore fast = mock(ArtifactStore.class);
        ArtifactStore slow = mock(ArtifactStore.class);
        try (var resolver = resolver(config -> {
            if (config.alId().equals("slow")) {
                entered.countDown();
                await(finish);
                return slow;
            }
            return fast;
        })) {
            try (var ignored = resolver.acquire("fast")) {
                Future<ArtifactStore> creating = callers.submit(() -> borrowedStore(resolver, "slow"));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    // When / Then: the slow constructor is still blocked.
                    assertThat(callers.submit(() -> borrowedStore(resolver, "fast")).get(2, TimeUnit.SECONDS))
                            .isSameAs(fast);
                    assertThat(callers.submit(resolver::snapshotStats).get(2, TimeUnit.SECONDS).cacheHits())
                            .isEqualTo(1);
                } finally {
                    finish.countDown();
                }
                assertThat(creating.get(5, TimeUnit.SECONDS)).isSameAs(slow);
            }
        }
    }

    @Test
    void slowRelease_shouldNotBlockAnIndependentCacheHit() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        ArtifactStore slow = mock(ArtifactStore.class);
        ArtifactStore fast = mock(ArtifactStore.class);
        ArtifactStoreProvider provider = new ArtifactStoreProvider() {
            @Override
            public ArtifactStore forConfig(OperationChainConfig config) {
                return config.alId().equals("slow") ? slow : fast;
            }

            @Override
            public void release(ArtifactStore store) {
                if (store == slow) {
                    entered.countDown();
                    await(finish);
                }
            }
        };
        try (var resolver = resolver(provider)) {
            borrowedStore(resolver, "slow");
            borrowedStore(resolver, "fast");
            Future<?> invalidating = callers.submit(() -> resolver.invalidate("slow"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                // When / Then
                assertThat(callers.submit(() -> borrowedStore(resolver, "fast")).get(2, TimeUnit.SECONDS))
                        .isSameAs(fast);
            } finally {
                finish.countDown();
            }
            invalidating.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void invalidationDuringConstruction_shouldKeepOnlyTheBorrowerLease() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var acquisitions = new AtomicInteger();
        var releases = new AtomicInteger();
        ArtifactStore store = mock(ArtifactStore.class);
        ArtifactStoreProvider provider = new ArtifactStoreProvider() {
            @Override
            public ArtifactStore forConfig(OperationChainConfig config) {
                if (acquisitions.incrementAndGet() == 1) {
                    entered.countDown();
                    await(finish);
                }
                return store;
            }

            @Override
            public void release(ArtifactStore ignored) {
                releases.incrementAndGet();
            }
        };
        try (var resolver = resolver(provider)) {
            Future<AssemblyLineStoreResolver.StoreLease> creating = callers.submit(() -> resolver.acquire("line"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                // When
                resolver.invalidate("line");
            } finally {
                finish.countDown();
            }
            try (var lease = creating.get(5, TimeUnit.SECONDS)) {
                // Then: invalidation cannot be undone by a late cache installation.
                assertThat(lease.store()).isSameAs(store);
                assertThat(resolver.snapshotStats().cachedAssemblyLines()).isZero();
                assertThat(releases).hasValue(0);
            }
            assertThat(releases).hasValue(1);
            borrowedStore(resolver, "line");
            assertThat(acquisitions).hasValue(2);
        }
        assertThat(releases).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void closeDuringConstruction_shouldWakeWaitersAndReleaseTheLateStore(boolean fatalCleanup) throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var releases = new AtomicInteger();
        var waitingThread = new AtomicReference<Thread>();
        var fatal = new AssertionError("late cleanup failed");
        ArtifactStoreProvider provider = new ArtifactStoreProvider() {
            @Override
            public ArtifactStore forConfig(OperationChainConfig config) {
                entered.countDown();
                await(finish);
                return mock(ArtifactStore.class);
            }

            @Override
            public void release(ArtifactStore ignored) {
                releases.incrementAndGet();
                if (fatalCleanup) {
                    throw fatal;
                }
            }
        };
        try (var resolver = resolver(provider)) {
            Future<?> creating = callers.submit(() -> borrowedStore(resolver, "line"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    return borrowedStore(resolver, "line");
                });
                awaitWaiting(waitingThread);
                // When
                callers.submit(resolver::close).get(2, TimeUnit.SECONDS);
                // Then: the waiter exits even while the constructor is still blocked.
                assertThat(failureOf(waiting)).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
            } finally {
                finish.countDown();
            }
            if (fatalCleanup) {
                assertThat(failureOf(creating)).isSameAs(fatal);
                assertThat(fatal.getSuppressed()).hasSize(1);
            } else {
                assertThat(failureOf(creating)).isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("closed");
            }
            assertThat(releases).hasValue(1);
            assertThat(resolver.snapshotStats().cachedAssemblyLines()).isZero();
        }
    }

    @Test
    void constructionFailure_shouldReachAllWaitersAndAllowRetry() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var waitingThread = new AtomicReference<Thread>();
        var failure = new AssertionError("constructor failed");
        try (var resolver = resolver(config -> {
            if (attempts.incrementAndGet() == 1) {
                entered.countDown();
                await(finish);
                throw failure;
            }
            return mock(ArtifactStore.class);
        })) {
            Future<?> creating = callers.submit(() -> borrowedStore(resolver, "line"));
            Future<?> waiting;
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    return borrowedStore(resolver, "line");
                });
                awaitWaiting(waitingThread);
            } finally {
                finish.countDown();
            }
            // When / Then
            assertThat(failureOf(creating)).isSameAs(failure);
            assertThat(failureOf(waiting)).isSameAs(failure);
            borrowedStore(resolver, "line");
            assertThat(attempts).hasValue(2);
        }
    }

    @Test
    void interruptedWaiter_shouldRetainInterruptAndLeaveCreationOwned() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var waitingThread = new AtomicReference<Thread>();
        var attempts = new AtomicInteger();
        try (var resolver = resolver(config -> {
            attempts.incrementAndGet();
            entered.countDown();
            await(finish);
            return mock(ArtifactStore.class);
        })) {
            Future<?> creating = callers.submit(() -> borrowedStore(resolver, "line"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                Future<Boolean> waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    assertThrows(IllegalStateException.class, () -> resolver.acquire("line"));
                    return Thread.currentThread().isInterrupted();
                });
                awaitWaiting(waitingThread);
                // When / Then
                waitingThread.get().interrupt();
                assertThat(waiting.get(2, TimeUnit.SECONDS)).isTrue();
                assertThat(attempts).hasValue(1);
            } finally {
                finish.countDown();
            }
            creating.get(5, TimeUnit.SECONDS);
        }
    }

    private static AssemblyLineStoreResolver resolver(ArtifactStoreProvider provider) {
        var repository = mock(OperationChainConfigRepository.class);
        when(repository.findByAssemblyLineId(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return Optional.of(new OperationChainConfig(id, false, StoreType.MEMORY, Map.of("id", id)));
        });
        return new AssemblyLineStoreResolver(repository, provider);
    }

    private static ArtifactStore borrowedStore(AssemblyLineStoreResolver resolver, String id) {
        try (var lease = resolver.acquire(id)) {
            return lease.store();
        }
    }

    private static Throwable failureOf(Future<?> future) {
        return assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS)).getCause();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private static void awaitWaiting(AtomicReference<Thread> thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.get() == null || thread.get().getState() != Thread.State.WAITING) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(1);
        }
    }
}
