package io.github.gear4jtest.external.api;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.exception.CompilationException;
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
        BoundedGeneratedSourceCompiler compiler = new BoundedGeneratedSourceCompiler(delegate, 4);
        byte[] source = "class Generated {}".getBytes(StandardCharsets.UTF_8);

        // When
        Map<String, byte[]> first = compiler.compile("io.test.Generated", source);
        first.get("io.test.Generated")[0] = 99;
        Map<String, byte[]> second = compiler.compile("io.test.Generated", source);

        // Then
        assertThat(compilations).hasValue(1);
        assertThat(second.get("io.test.Generated")).containsExactly(1, 2, 3);
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
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("delegate was not released");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return Map.of(className, new byte[] { 7 });
        };
        BoundedGeneratedSourceCompiler compiler = new BoundedGeneratedSourceCompiler(delegate, 4);
        var executor = Executors.newFixedThreadPool(2);
        try {
            // When
            var first = executor.submit(() -> compiler.compile("io.test.Generated", new byte[] { 1 }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> compiler.compile("io.test.Generated", new byte[] { 1 }));
            release.countDown();

            // Then
            assertThat(first.get(5, TimeUnit.SECONDS)).containsKey("io.test.Generated");
            assertThat(second.get(5, TimeUnit.SECONDS)).containsKey("io.test.Generated");
            assertThat(compilations).hasValue(1);
        } finally {
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
        BoundedGeneratedSourceCompiler compiler = new BoundedGeneratedSourceCompiler(delegate, 2);

        // When
        compiler.compile("io.test.One", new byte[] { 1 });
        compiler.compile("io.test.Two", new byte[] { 2 });
        compiler.compile("io.test.One", new byte[] { 1 });
        compiler.compile("io.test.Three", new byte[] { 3 });
        compiler.compile("io.test.Two", new byte[] { 2 });

        // Then
        assertThat(compilations).hasValue(4);
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
        BoundedGeneratedSourceCompiler compiler = new BoundedGeneratedSourceCompiler(delegate, 2);

        // When / Then
        assertThatThrownBy(() -> compiler.compile("io.test.Generated", new byte[] { 1 }))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("temporary failure");
        assertThat(compiler.compile("io.test.Generated", new byte[] { 1 }))
                .containsKey("io.test.Generated");
        assertThat(compilations).hasValue(2);
    }

    @Test
    void compile_shouldNotCacheEntryLargerThanBytecodeBudget() {
        // Given
        AtomicInteger compilations = new AtomicInteger();
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            compilations.incrementAndGet();
            return Map.of(className, new byte[] { 1, 2, 3, 4 });
        };
        BoundedGeneratedSourceCompiler compiler = new BoundedGeneratedSourceCompiler(delegate, 4, 3L);

        // When
        compiler.compile("io.test.Generated", new byte[] { 1 });
        compiler.compile("io.test.Generated", new byte[] { 1 });

        // Then
        assertThat(compilations).hasValue(2);
    }
}
