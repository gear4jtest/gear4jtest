package io.github.gear4jtest.external.api.spi;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.storage.DefaultArtifactStoreProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DefaultArtifactStoreProviderConcurrencyTest {
    private final ExecutorService callers = Executors.newFixedThreadPool(4);

    @AfterEach
    void stopCallers() throws Exception {
        callers.shutdownNow();
        assertThat(callers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void slowConstruction_shouldShareOneCreationAndAllowIndependentHits() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var waitingThread = new AtomicReference<Thread>();
        var creations = new AtomicInteger();
        ArtifactStore fast = mock(ArtifactStore.class);
        ArtifactStore slow = mock(ArtifactStore.class);
        try (var provider = provider(id -> {
            creations.incrementAndGet();
            if (id.equals("slow")) {
                entered.countDown();
                await(finish);
                return slow;
            }
            return fast;
        })) {
            provider.forConfig(config("fast"));
            Future<ArtifactStore> creating = callers.submit(() -> provider.forConfig(config("slow")));
            Future<ArtifactStore> waiting;
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    return provider.forConfig(config("slow"));
                });
                awaitWaiting(waitingThread);
                // When / Then
                assertThat(callers.submit(() -> provider.forConfig(config("fast"))).get(2, TimeUnit.SECONDS))
                        .isSameAs(fast);
                assertThat(creations).hasValue(2);
            } finally {
                finish.countDown();
            }
            assertThat(creating.get(5, TimeUnit.SECONDS)).isSameAs(slow);
            assertThat(waiting.get(5, TimeUnit.SECONDS)).isSameAs(slow);
            provider.release(slow);
            verify(slow, never()).close();
            provider.release(slow);
            verify(slow).close();
            provider.release(fast);
            verify(fast, never()).close();
            provider.release(fast);
            verify(fast).close();
        }
        verify(slow).close();
        verify(fast).close();
    }

    @Test
    void slowClose_shouldNotBlockOtherKeysAndShouldSerializeSameKeyRecreation() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var waitingThread = new AtomicReference<Thread>();
        var slowCreations = new AtomicInteger();
        ArtifactStore first = mock(ArtifactStore.class);
        ArtifactStore replacement = mock(ArtifactStore.class);
        ArtifactStore fast = mock(ArtifactStore.class);
        doAnswer(invocation -> {
            entered.countDown();
            await(finish);
            return null;
        }).when(first).close();
        try (var provider = provider(id -> id.equals("fast") ? fast
                : slowCreations.incrementAndGet() == 1 ? first : replacement)) {
            provider.forConfig(config("slow"));
            provider.forConfig(config("fast"));
            Future<?> retiring = callers.submit(() -> provider.release(first));
            Future<ArtifactStore> waiting;
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    return provider.forConfig(config("slow"));
                });
                awaitWaiting(waitingThread);
                // When / Then
                assertThat(callers.submit(() -> provider.forConfig(config("fast"))).get(2, TimeUnit.SECONDS))
                        .isSameAs(fast);
                assertThat(slowCreations).hasValue(1);
            } finally {
                finish.countDown();
            }
            retiring.get(5, TimeUnit.SECONDS);
            assertThat(waiting.get(5, TimeUnit.SECONDS)).isSameAs(replacement);
            provider.release(replacement);
            provider.release(fast);
            provider.release(fast);
        }
        verify(first).close();
        verify(replacement).close();
    }

    @Test
    void closeDuringConstruction_shouldWakeWaitersAndDisposeLateResultOnce() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var waitingThread = new AtomicReference<Thread>();
        ArtifactStore late = mock(ArtifactStore.class);
        try (var provider = provider(id -> {
            entered.countDown();
            await(finish);
            return late;
        })) {
            Future<?> creating = callers.submit(() -> provider.forConfig(config("slow")));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    return provider.forConfig(config("slow"));
                });
                awaitWaiting(waitingThread);
                // When / Then
                callers.submit(provider::close).get(2, TimeUnit.SECONDS);
                assertThat(failureOf(waiting)).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
            } finally {
                finish.countDown();
            }
            assertThat(failureOf(creating)).isInstanceOf(IllegalStateException.class).hasMessageContaining("closed");
            verify(late).close();
            assertThat(callers.isShutdown()).isFalse();
        }
        verify(late).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void sharedInstanceRetiredDuringAnotherConstruction_shouldNotBeInstalledOrClosedTwice(boolean shutdown)
            throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        ArtifactStore shared = mock(ArtifactStore.class);
        try (var provider = provider(id -> {
            if (id.equals("second")) {
                entered.countDown();
                await(finish);
            }
            return shared;
        })) {
            provider.forConfig(config("first"));
            Future<?> creating = callers.submit(() -> provider.forConfig(config("second")));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                // When: retirement finishes before the overlapping constructor returns its
                // alias.
                if (shutdown) {
                    provider.close();
                } else {
                    provider.release(shared);
                }
                verify(shared).close();
            } finally {
                finish.countDown();
            }
            // Then
            assertThat(failureOf(creating)).isInstanceOf(IllegalStateException.class).hasMessageContaining("retired");
        }
        verify(shared).close();
    }

    @Test
    void fatalConstructionFailure_shouldReachWaitersAndAllowRetry() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var waitingThread = new AtomicReference<Thread>();
        var attempts = new AtomicInteger();
        var fatal = new AssertionError("plugin failure");
        ArtifactStore store = mock(ArtifactStore.class);
        try (var provider = provider(id -> {
            if (attempts.incrementAndGet() == 1) {
                entered.countDown();
                await(finish);
                throw fatal;
            }
            return store;
        })) {
            Future<?> creating = callers.submit(() -> provider.forConfig(config("line")));
            Future<?> waiting;
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    return provider.forConfig(config("line"));
                });
                awaitWaiting(waitingThread);
            } finally {
                finish.countDown();
            }
            // When / Then
            assertThat(failureOf(creating)).isSameAs(fatal);
            assertThat(failureOf(waiting)).isSameAs(fatal);
            assertThat(provider.forConfig(config("line"))).isSameAs(store);
            assertThat(attempts).hasValue(2);
            provider.release(store);
        }
    }

    @Test
    void close_shouldAttemptEveryStoreAndPreserveFatalCleanup() {
        // Given
        ArtifactStore first = mock(ArtifactStore.class);
        ArtifactStore second = mock(ArtifactStore.class);
        var ordinary = new IllegalStateException("first close failed");
        var fatal = new AssertionError("second close failed");
        doThrow(ordinary).when(first).close();
        doThrow(fatal).when(second).close();
        var provider = provider(id -> id.equals("first") ? first : second);
        provider.forConfig(config("first"));
        provider.forConfig(config("second"));

        // When / Then: iteration order must not decide whether Error escapes.
        assertThat(assertThrows(AssertionError.class, provider::close)).isSameAs(fatal);
        assertThat(fatal.getSuppressed()).containsExactly(ordinary);
        provider.close();
        verify(first).close();
        verify(second).close();
        assertThat(callers.isShutdown()).isFalse();
    }

    @Test
    void interruptedWaiter_shouldNotCancelTheSharedConstruction() throws Exception {
        // Given
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var waitingThread = new AtomicReference<Thread>();
        ArtifactStore store = mock(ArtifactStore.class);
        try (var provider = provider(id -> {
            entered.countDown();
            await(finish);
            return store;
        })) {
            Future<ArtifactStore> creating = callers.submit(() -> provider.forConfig(config("line")));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                Future<Boolean> waiting = callers.submit(() -> {
                    waitingThread.set(Thread.currentThread());
                    assertThrows(IllegalStateException.class, () -> provider.forConfig(config("line")));
                    return Thread.currentThread().isInterrupted();
                });
                awaitWaiting(waitingThread);
                // When / Then
                waitingThread.get().interrupt();
                assertThat(waiting.get(2, TimeUnit.SECONDS)).isTrue();
                verify(store, never()).close();
            } finally {
                finish.countDown();
            }
            assertThat(creating.get(5, TimeUnit.SECONDS)).isSameAs(store);
            provider.release(store);
        }
        verify(store).close();
    }

    @Test
    void partialConstructionFailure_shouldPreserveEveryCleanupFailure() {
        // Given
        ArtifactStore primary = mock(ArtifactStore.class);
        ArtifactStore fallback = mock(ArtifactStore.class);
        var original = new IllegalStateException("second fallback failed");
        var closePrimary = new IllegalStateException("primary cleanup failed");
        var closeFallback = new IllegalStateException("fallback cleanup failed");
        doThrow(closePrimary).when(primary).close();
        doThrow(closeFallback).when(fallback).close();
        try (var provider = provider(id -> {
            if (id.equals("bad")) {
                throw original;
            }
            return id.equals("primary") ? primary : fallback;
        })) {
            var config = new OperationChainConfig("line", false, StoreType.of("PROBE"), Map.of(
                                                                                               "id", "primary",
                                                                                               "fallback.1.type",
                                                                                               "PROBE",
                                                                                               "fallback.1.props.id",
                                                                                               "fallback",
                                                                                               "fallback.2.type",
                                                                                               "PROBE",
                                                                                               "fallback.2.props.id",
                                                                                               "bad"));
            // When / Then
            var reported = assertThrows(RuntimeException.class, () -> provider.forConfig(config));
            assertThat(reported).hasCause(original);
            assertThat(reported.getSuppressed()).containsExactlyInAnyOrder(closePrimary, closeFallback);
            verify(primary).close();
            verify(fallback).close();
        }
    }

    private DefaultArtifactStoreProvider provider(Function<String, ArtifactStore> factory) {
        var plugin = new ArtifactStorePlugin() {
            @Override
            public String type() {
                return "PROBE";
            }

            @Override
            public ArtifactStore build(Map<String, String> properties, Context context) {
                return factory.apply(properties.get("id"));
            }
        };
        return new DefaultArtifactStoreProvider(new ArtifactStoreResolver(List.of(plugin)), null, callers);
    }

    private static OperationChainConfig config(String id) {
        return new OperationChainConfig(id, false, StoreType.of("PROBE"), Map.of("id", id));
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
