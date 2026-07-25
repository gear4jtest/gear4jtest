package io.github.gear4jtest.external.api;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.exception.CompilationException;
import io.github.gear4jtest.external.api.exception.CompilationTimeoutException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedGeneratedSourceCompilerTest {
    @Test
    void compile_shouldReuseCompletedCompilationAndDefensivelyCopyBytecode() {
        // Given
        AtomicInteger compilations = new AtomicInteger();
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            compilations.incrementAndGet();
            return Map.of(className, new byte[] { 1, 2, 3 });
        };
        byte[] source = "class Generated {}".getBytes(StandardCharsets.UTF_8);

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 4)) {
            // When
            Map<String, byte[]> first = compiler.compile("io.test.Generated", source);
            first.get("io.test.Generated")[0] = 99;
            Map<String, byte[]> second = compiler.compile("io.test.Generated", source);

            // Then
            assertThat(compilations).hasValue(1);
            assertThat(second.get("io.test.Generated")).containsExactly(1, 2, 3);
            assertThat(compiler.snapshotStats())
                    .extracting(GeneratedCompilationStats::cacheHits,
                                GeneratedCompilationStats::cacheMisses,
                                GeneratedCompilationStats::startedCompilations,
                                GeneratedCompilationStats::successfulCompilations)
                    .containsExactly(1L, 1L, 1L, 1L);
            assertThat(compiler.snapshotStats().totalCompilationDurationNanos()).isPositive();
        }
    }

    @Test
    void compile_shouldSingleFlightConcurrentRequests() throws Exception {
        // Given
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            compilations.incrementAndGet();
            entered.countDown();
            await(release);
            return Map.of(className, new byte[] { 7 });
        };
        var executor = Executors.newFixedThreadPool(2);
        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 4)) {
            // When
            var first = executor.submit(() -> compiler.compile("io.test.Generated", new byte[] { 1 }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> compiler.compile("io.test.Generated", new byte[] { 1 }));
            awaitStats(() -> compiler.snapshotStats().singleFlightJoins() == 1L);
            release.countDown();

            // Then
            assertThat(first.get(5, TimeUnit.SECONDS)).containsKey("io.test.Generated");
            assertThat(second.get(5, TimeUnit.SECONDS)).containsKey("io.test.Generated");
            assertThat(compilations).hasValue(1);
            assertThat(compiler.snapshotStats().singleFlightJoins()).isEqualTo(1L);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void compile_shouldEvictLeastRecentlyUsedEntry() {
        // Given
        AtomicInteger compilations = new AtomicInteger();
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            compilations.incrementAndGet();
            return Map.of(className, sourceCode.clone());
        };

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 2)) {
            // When
            compiler.compile("io.test.One", new byte[] { 1 });
            compiler.compile("io.test.Two", new byte[] { 2 });
            compiler.compile("io.test.One", new byte[] { 1 });
            compiler.compile("io.test.Three", new byte[] { 3 });
            compiler.compile("io.test.Two", new byte[] { 2 });

            // Then
            assertThat(compilations).hasValue(4);
            assertThat(compiler.snapshotStats().cachedEntries()).isEqualTo(2);
        }
    }

    @Test
    void compile_shouldNotCacheFailures() {
        // Given
        AtomicInteger compilations = new AtomicInteger();
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            if (compilations.incrementAndGet() == 1) {
                throw new CompilationException("temporary failure");
            }
            return Map.of(className, new byte[] { 1 });
        };

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 2)) {
            // When / Then
            assertThatThrownBy(() -> compiler.compile("io.test.Generated", new byte[] { 1 }))
                    .isInstanceOf(CompilationException.class)
                    .hasMessageContaining("temporary failure");
            assertThat(compiler.compile("io.test.Generated", new byte[] { 1 }))
                    .containsKey("io.test.Generated");
            assertThat(compilations).hasValue(2);
            assertThat(compiler.snapshotStats().failedCompilations()).isEqualTo(1L);
        }
    }

    @Test
    void compile_shouldNotCacheEntryLargerThanBytecodeBudget() {
        // Given
        AtomicInteger compilations = new AtomicInteger();
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            compilations.incrementAndGet();
            return Map.of(className, new byte[] { 1, 2, 3, 4 });
        };

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 4, 3L)) {
            // When
            compiler.compile("io.test.Generated", new byte[] { 1 });
            compiler.compile("io.test.Generated", new byte[] { 1 });

            // Then
            assertThat(compilations).hasValue(2);
            assertThat(compiler.snapshotStats().cachedEntries()).isZero();
        }
    }

    @Test
    void compile_shouldSaturateVeryLargeTimeoutInsteadOfOverflowing() {
        // Given
        GeneratedSourceCompiler delegate = (className, sourceCode) -> Map.of(className, new byte[] { 1 });
        var configuration = new GeneratedCompilationConfiguration(Duration.ofSeconds(Long.MAX_VALUE), 1, 1);

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 4,
                BoundedGeneratedSourceCompiler.DEFAULT_MAX_BYTECODE_BYTES, configuration)) {
            // When / Then
            assertThat(compiler.compile("io.test.Generated", new byte[] { 1 }))
                    .containsKey("io.test.Generated");
            assertThat(compiler.snapshotStats().timedOutCompilations()).isZero();
        }
    }

    @Test
    void compile_shouldTimeoutOwnerWakeWaitersDiscardLateResultAndAllowRetry() throws Exception {
        // Given
        AtomicInteger compilations = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            if (compilations.incrementAndGet() == 1) {
                firstEntered.countDown();
                awaitIgnoringInterruption(releaseFirst);
            }
            return Map.of(className, new byte[] { 1 });
        };
        var configuration = new GeneratedCompilationConfiguration(Duration.ofMillis(100), 1, 2);
        var callers = Executors.newFixedThreadPool(2);

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 4,
                BoundedGeneratedSourceCompiler.DEFAULT_MAX_BYTECODE_BYTES, configuration)) {
            var owner = callers.submit(() -> compiler.compile("io.test.Generated", new byte[] { 1 }));
            assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var waiter = callers.submit(() -> compiler.compile("io.test.Generated", new byte[] { 1 }));

            // When / Then
            assertTimeout(() -> owner.get(2, TimeUnit.SECONDS));
            assertTimeout(() -> waiter.get(2, TimeUnit.SECONDS));
            awaitStats(() -> compiler.snapshotStats().inFlightCompilations() == 0);
            assertThat(compiler.snapshotStats().timedOutCompilations()).isEqualTo(1L);

            releaseFirst.countDown();
            awaitStats(() -> compiler.snapshotStats().activeCompilations() == 0);
            assertThat(compiler.compile("io.test.Generated", new byte[] { 1 }))
                    .containsKey("io.test.Generated");
            assertThat(compilations).hasValue(2);
        } finally {
            releaseFirst.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void compile_shouldRejectNewDistinctFlightWhenExecutorQueueIsFull() throws Exception {
        // Given
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            if ("io.test.One".equals(className)) {
                firstEntered.countDown();
                await(release);
            }
            return Map.of(className, new byte[] { 1 });
        };
        var configuration = new GeneratedCompilationConfiguration(Duration.ofSeconds(5), 1, 1);
        var callers = Executors.newFixedThreadPool(2);

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 4,
                BoundedGeneratedSourceCompiler.DEFAULT_MAX_BYTECODE_BYTES, configuration)) {
            var first = callers.submit(() -> compiler.compile("io.test.One", new byte[] { 1 }));
            assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = callers.submit(() -> compiler.compile("io.test.Two", new byte[] { 2 }));
            awaitStats(() -> compiler.snapshotStats().queuedCompilations() == 1);

            // When / Then
            assertThatThrownBy(() -> compiler.compile("io.test.Three", new byte[] { 3 }))
                    .isInstanceOf(CompilationException.class)
                    .hasMessageContaining("saturated");
            assertThat(compiler.snapshotStats().rejectedCompilations()).isEqualTo(1L);

            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).containsKey("io.test.One");
            assertThat(queued.get(2, TimeUnit.SECONDS)).containsKey("io.test.Two");
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void close_shouldCancelWaitersAndRejectNewCompilation() throws Exception {
        // Given
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            entered.countDown();
            awaitIgnoringInterruption(release);
            return Map.of(className, new byte[] { 1 });
        };
        var configuration = new GeneratedCompilationConfiguration(Duration.ofSeconds(5), 1, 1);
        var caller = Executors.newSingleThreadExecutor();
        var compiler = new BoundedGeneratedSourceCompiler(delegate, 4,
                BoundedGeneratedSourceCompiler.DEFAULT_MAX_BYTECODE_BYTES, configuration);

        try {
            var result = caller.submit(() -> compiler.compile("io.test.Generated", new byte[] { 1 }));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            compiler.close();

            // Then
            assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(CompilationException.class)
                    .hasRootCauseMessage("Generated-source compilation runtime is closed");
            assertThatThrownBy(() -> compiler.compile("io.test.Other", new byte[] { 2 }))
                    .isInstanceOf(CompilationException.class)
                    .hasMessageContaining("runtime is closed");
            assertThat(compiler.snapshotStats().shutdown()).isTrue();
        } finally {
            release.countDown();
            compiler.close();
            caller.shutdownNow();
        }
    }

    private static void assertTimeout(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(CompilationTimeoutException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }

    private static void awaitIgnoringInterruption(CountDownLatch latch) {
        boolean released = false;
        while (!released) {
            try {
                released = latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Deliberately non-cooperative delegate used to prove best-effort timeout.
            }
        }
    }

    private static void awaitStats(Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.evaluate() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(condition.evaluate()).isTrue();
    }

    @FunctionalInterface
    private interface Condition {
        boolean evaluate();
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
