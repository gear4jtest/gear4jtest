package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class GeneratedLoadingRuntimeTest {
    private static final GeneratedAssemblyLine<Object, Object> GENERATED = () -> null;

    @Test
    void load_shouldRegisterOnceThenUseCachedInstance() throws Exception {
        // Given
        AtomicReference<GeneratedAssemblyLine<?, ?>> cached = new AtomicReference<>();
        AtomicInteger loads = new AtomicInteger();
        GeneratedLoadingRuntime.LoadingOperation operation = new GeneratedLoadingRuntime.LoadingOperation() {
            @Override
            public GeneratedAssemblyLine<?, ?> findCached() {
                return cached.get();
            }

            @Override
            public GeneratedLoadingRuntime.LoadResult load(GeneratedLoadingRuntime.LoadAttempt attempt) {
                loads.incrementAndGet();
                attempt.recordArtifactReadDuration(1L);
                attempt.recordTranslationDuration(2L);
                attempt.recordCompilationDuration(3L);
                attempt.recordInstantiationDuration(4L);
                return new GeneratedLoadingRuntime.LoadResult(GENERATED, () -> cached.set(GENERATED));
            }
        };

        try (var runtime = new GeneratedLoadingRuntime(GeneratedLoadingConfiguration.defaults())) {
            // When
            GeneratedAssemblyLine<?, ?> first = runtime.load("loader", operation);
            GeneratedAssemblyLine<?, ?> second = runtime.load("loader", operation);

            // Then
            assertThat(first).isSameAs(GENERATED);
            assertThat(second).isSameAs(GENERATED);
            assertThat(loads).hasValue(1);
            assertThat(runtime.snapshotStats())
                    .extracting(GeneratedLoadingStats::cacheHits,
                                GeneratedLoadingStats::cacheMisses,
                                GeneratedLoadingStats::startedLoads,
                                GeneratedLoadingStats::successfulLoads)
                    .containsExactly(1L, 1L, 1L, 1L);
            assertThat(runtime.snapshotStats().artifactReadDurationNanos()).isEqualTo(1L);
            assertThat(runtime.snapshotStats().translationDurationNanos()).isEqualTo(2L);
            assertThat(runtime.snapshotStats().compilationDurationNanos()).isEqualTo(3L);
            assertThat(runtime.snapshotStats().instantiationDurationNanos()).isEqualTo(4L);
        }
    }

    @Test
    void load_shouldUseCachePopulatedBetweenCallerCheckAndWorkerStart() throws Exception {
        // Given
        AtomicInteger cacheLookups = new AtomicInteger();
        AtomicInteger loads = new AtomicInteger();
        GeneratedLoadingRuntime.LoadingOperation operation = new GeneratedLoadingRuntime.LoadingOperation() {
            @Override
            public GeneratedAssemblyLine<?, ?> findCached() {
                return cacheLookups.incrementAndGet() == 1 ? null : GENERATED;
            }

            @Override
            public GeneratedLoadingRuntime.LoadResult load(GeneratedLoadingRuntime.LoadAttempt attempt) {
                loads.incrementAndGet();
                return new GeneratedLoadingRuntime.LoadResult(GENERATED, () -> {
                });
            }
        };

        try (var runtime = new GeneratedLoadingRuntime(GeneratedLoadingConfiguration.defaults())) {
            // When
            GeneratedAssemblyLine<?, ?> result = runtime.load("loader", operation);

            // Then
            assertThat(result).isSameAs(GENERATED);
            assertThat(loads).hasValue(0);
            assertThat(runtime.snapshotStats().cacheHits()).isEqualTo(1L);
            assertThat(runtime.snapshotStats().startedLoads()).isZero();
        }
    }

    @Test
    void load_shouldRemoveFailedFlightBeforeRetry() throws Exception {
        // Given
        AtomicInteger loads = new AtomicInteger();
        GeneratedLoadingRuntime.LoadingOperation operation = attempt -> {
            if (loads.incrementAndGet() == 1) {
                throw new IOException("temporary load failure");
            }
            return new GeneratedLoadingRuntime.LoadResult(GENERATED, () -> {
            });
        };

        try (var runtime = new GeneratedLoadingRuntime(GeneratedLoadingConfiguration.defaults())) {
            // When / Then
            assertThatThrownBy(() -> runtime.load("loader", operation))
                    .isInstanceOf(IOException.class)
                    .hasMessage("temporary load failure");
            assertThat(runtime.load("loader", operation)).isSameAs(GENERATED);
            assertThat(loads).hasValue(2);
            assertThat(runtime.snapshotStats().failedLoads()).isEqualTo(1L);
        }
    }

    @Test
    void load_shouldPropagateRegistrationFailureAndAllowRetry() throws Exception {
        // Given
        AtomicInteger registrations = new AtomicInteger();
        GeneratedLoadingRuntime.LoadingOperation operation = attempt -> new GeneratedLoadingRuntime.LoadResult(
                GENERATED, () -> {
                    if (registrations.incrementAndGet() == 1) {
                        throw new IllegalStateException("registry unavailable");
                    }
                });

        try (var runtime = new GeneratedLoadingRuntime(GeneratedLoadingConfiguration.defaults())) {
            // When / Then
            assertThatThrownBy(() -> runtime.load("loader", operation))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("registry unavailable");
            assertThat(runtime.load("loader", operation)).isSameAs(GENERATED);
            assertThat(registrations).hasValue(2);
            assertThat(runtime.snapshotStats().failedLoads()).isEqualTo(1L);
        }
    }

    @Test
    void load_shouldReleaseFailedFlightWhenRegistrationCleanupAlsoFails() throws Exception {
        // Given
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger cleanups = new AtomicInteger();
        GeneratedLoadingRuntime.LoadingOperation operation = attempt -> new GeneratedLoadingRuntime.LoadResult(
                GENERATED,
                () -> {
                    if (registrations.incrementAndGet() == 1) {
                        throw new IllegalStateException("registry unavailable");
                    }
                },
                () -> {
                    if (cleanups.incrementAndGet() == 1) {
                        throw new IllegalStateException("registry cleanup failed");
                    }
                });

        try (var runtime = new GeneratedLoadingRuntime(GeneratedLoadingConfiguration.defaults())) {
            // When
            Throwable failure = catchThrowable(() -> runtime.load("loader", operation));
            awaitFlightRelease(runtime);

            // Then
            assertThat(failure)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("registry unavailable");
            assertThat(failure.getSuppressed()).singleElement()
                    .asInstanceOf(InstanceOfAssertFactories.THROWABLE)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("registry cleanup failed");
            assertThat(runtime.snapshotStats().inFlightLoads()).isZero();
            assertThat(runtime.load("loader", operation)).isSameAs(GENERATED);
            assertThat(registrations).hasValue(2);
        }
    }

    @Test
    void close_shouldWakeInFlightCallerAndRejectLaterLoads() throws Exception {
        // Given
        CountDownLatch loadEntered = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        GeneratedLoadingRuntime.LoadingOperation operation = attempt -> {
            loadEntered.countDown();
            awaitIgnoringInterruption(releaseLoad);
            return new GeneratedLoadingRuntime.LoadResult(GENERATED, () -> {
            });
        };
        GeneratedLoadingRuntime runtime = new GeneratedLoadingRuntime(
                new GeneratedLoadingConfiguration(Duration.ofSeconds(5), 1, 1));
        ExecutorService caller = Executors.newSingleThreadExecutor();

        try {
            Future<GeneratedAssemblyLine<?, ?>> result = caller.submit(() -> runtime.load("loader", operation));
            assertThat(loadEntered.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            runtime.close();

            // Then
            assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(IOException.class)
                    .hasMessageContaining("loading runtime is closed");
            assertThatThrownBy(() -> runtime.load("other", operation))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("loading runtime is closed");
            assertThat(runtime.snapshotStats().shutdown()).isTrue();
        } finally {
            releaseLoad.countDown();
            caller.shutdownNow();
            runtime.close();
        }
    }

    private static void awaitIgnoringInterruption(CountDownLatch latch) {
        boolean released = false;
        while (!released) {
            try {
                released = latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Deliberately non-cooperative test operation.
            }
        }
    }

    private static void awaitFlightRelease(GeneratedLoadingRuntime runtime) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (runtime.snapshotStats().inFlightLoads() != 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10L);
        }
        assertThat(runtime.snapshotStats().inFlightLoads())
                .as("registration cleanup must not retain a completed single-flight")
                .isZero();
    }
}
