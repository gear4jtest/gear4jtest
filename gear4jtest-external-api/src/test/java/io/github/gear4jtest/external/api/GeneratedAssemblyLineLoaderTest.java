package io.github.gear4jtest.external.api;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import io.github.gear4jtest.external.api.exception.CompilationTimeoutException;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.storage.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedAssemblyLineLoaderTest {
    private static final String GENERATED_CLASS = "io.github.gear4jtest.generated.ConcurrentGenerated";
    private static final String GENERATED_SOURCE = """
            package io.github.gear4jtest.generated;

            public final class ConcurrentGenerated
                    implements io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine {
                @Override
                public io.github.gear4jtest.core.api.AssemblyLine getAssemblyLineDefinition() {
                    return null;
                }
            }
            """;

    @Test
    void loadOrCompile_shouldCompileOnlyOnceForConcurrentArtifactLookups() throws Exception {
        // Given
        int concurrentLookups = 16;
        Map<String, byte[]> compiledClasses = compileGeneratedClass();
        CountDownLatch compilerEntered = new CountDownLatch(1);
        CountDownLatch releaseCompiler = new CountDownLatch(1);
        AtomicInteger compilationCount = new AtomicInteger();
        GeneratedSourceCompiler compiler = (className, sourceCode) -> {
            compilationCount.incrementAndGet();
            compilerEntered.countDown();
            await(releaseCompiler);
            return compiledClasses;
        };
        CoordinatedRegistry registry = new CoordinatedRegistry(concurrentLookups);
        LoaderFixture fixture = fixture(compiler, registry);
        ExecutorService callers = Executors.newFixedThreadPool(concurrentLookups);

        try {
            List<Future<GeneratedAssemblyLine>> results = new ArrayList<>();
            for (int index = 0; index < concurrentLookups; index++) {
                results.add(callers.submit(() -> fixture.loader().loadOrCompile("line", fixture.object())));
            }
            assertThat(compilerEntered.await(2, TimeUnit.SECONDS)).isTrue();

            // When
            releaseCompiler.countDown();

            // Then
            GeneratedAssemblyLine expected = results.get(0).get(5, TimeUnit.SECONDS);
            for (Future<GeneratedAssemblyLine> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(expected);
            }
            assertThat(compilationCount).hasValue(1);
        } finally {
            releaseCompiler.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void loadOrCompile_shouldAllowRetryAfterFailedSingleFlight() throws Exception {
        // Given
        Map<String, byte[]> compiledClasses = compileGeneratedClass();
        AtomicInteger compilationCount = new AtomicInteger();
        GeneratedSourceCompiler compiler = (className, sourceCode) -> {
            if (compilationCount.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary compiler failure");
            }
            return compiledClasses;
        };
        LoaderFixture fixture = fixture(compiler, InMemoryClassLoaderRegistry.builder().build());

        // When / Then
        assertThatThrownBy(() -> fixture.loader().loadOrCompile("line", fixture.object()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary compiler failure");
        assertThat(fixture.loader().loadOrCompile("line", fixture.object())).isNotNull();
        assertThat(compilationCount).hasValue(2);
    }

    @Test
    void loadOrCompile_shouldWakeAllWaitersAndAllowRetryAfterCompilationTimeout() throws Exception {
        // Given
        Map<String, byte[]> compiledClasses = compileGeneratedClass();
        AtomicInteger compilationCount = new AtomicInteger();
        CountDownLatch compilerEntered = new CountDownLatch(1);
        CountDownLatch releaseCompiler = new CountDownLatch(1);
        GeneratedSourceCompiler delegate = (className, sourceCode) -> {
            if (compilationCount.incrementAndGet() == 1) {
                compilerEntered.countDown();
                awaitIgnoringInterruption(releaseCompiler);
            }
            return compiledClasses;
        };
        var configuration = new GeneratedCompilationConfiguration(Duration.ofMillis(100), 1, 2);
        ExecutorService callers = Executors.newFixedThreadPool(2);

        try (var compiler = new BoundedGeneratedSourceCompiler(delegate, 4,
                BoundedGeneratedSourceCompiler.DEFAULT_MAX_BYTECODE_BYTES, configuration)) {
            LoaderFixture fixture = fixture(compiler, new CoordinatedRegistry(2));
            Future<GeneratedAssemblyLine> owner = callers
                    .submit(() -> fixture.loader().loadOrCompile("line", fixture.object()));
            Future<GeneratedAssemblyLine> waiter = callers
                    .submit(() -> fixture.loader().loadOrCompile("line", fixture.object()));
            assertThat(compilerEntered.await(2, TimeUnit.SECONDS)).isTrue();

            // When / Then
            assertThatThrownBy(() -> owner.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(CompilationTimeoutException.class);
            assertThatThrownBy(() -> waiter.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(CompilationTimeoutException.class);

            releaseCompiler.countDown();
            awaitStats(() -> compiler.snapshotStats().activeCompilations() == 0);
            assertThat(fixture.loader().loadOrCompile("line", fixture.object())).isNotNull();
            assertThat(compilationCount).hasValue(2);
        } finally {
            releaseCompiler.countDown();
            callers.shutdownNow();
        }
    }

    private static LoaderFixture fixture(GeneratedSourceCompiler compiler, ClassLoaderRegistry registry)
            throws Exception {
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        byte[] artifactBytes = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        String hash = artifactStore.put(artifactBytes);
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.MEMORY, Map.of());
        OperationChainObject object = new OperationChainObject(null, "line", "1.0.0", ExecutionMode.TEST, hash,
                artifactBytes.length, "application/xml", Instant.parse("2026-07-13T08:00:00Z"), "tester",
                Instant.parse("2026-07-13T08:00:00Z"));
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        when(storeProvider.forConfig(config)).thenReturn(artifactStore);
        OperationChainTranslator translator = mock(OperationChainTranslator.class);
        when(translator.translate(any(byte[].class), eq("application/xml")))
                .thenReturn(new OperationChainTranslator.GenerationResult(GENERATED_CLASS, GENERATED_SOURCE));
        OperationChainTranslatorResolver translatorResolver = mock(OperationChainTranslatorResolver.class);
        when(translatorResolver.resolve("application/xml")).thenReturn(translator);
        AssemblyLineStoreResolver storeResolver = new AssemblyLineStoreResolver(configRepository, storeProvider);
        GeneratedAssemblyLineLoader loader = new GeneratedAssemblyLineLoader(storeResolver, registry,
                translatorResolver, compiler, new SimpleDependencyInjector(),
                GeneratedAssemblyLineLoaderTest.class.getClassLoader(), ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
        return new LoaderFixture(loader, object);
    }

    private static Map<String, byte[]> compileGeneratedClass() {
        return new JavaxToolsGeneratedSourceCompiler(GeneratedAssemblyLineLoaderTest.class.getClassLoader())
                .compile(GENERATED_CLASS, GENERATED_SOURCE.getBytes(StandardCharsets.UTF_8));
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
                // Deliberately non-cooperative compiler used to prove timeout cleanup.
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

    private record LoaderFixture(GeneratedAssemblyLineLoader loader, OperationChainObject object) {}

    private static final class CoordinatedRegistry implements ClassLoaderRegistry {
        private final ClassLoaderRegistry delegate = InMemoryClassLoaderRegistry.builder().build();
        private final CountDownLatch initialLookups;

        private CoordinatedRegistry(int concurrentLookups) {
            this.initialLookups = new CountDownLatch(concurrentLookups);
        }

        @Override
        public ClassLoader get(String internalLoaderId) {
            initialLookups.countDown();
            await(initialLookups);
            return delegate.get(internalLoaderId);
        }

        @Override
        public void register(String internalLoaderId, ClassLoader loader, GeneratedAssemblyLine bound) {
            delegate.register(internalLoaderId, loader, bound);
        }

        @Override
        public void evict(String internalLoaderId) {
            delegate.evict(internalLoaderId);
        }

        @Override
        public void setAlias(String alias, String internalLoaderId) {
            delegate.setAlias(alias, internalLoaderId);
        }

        @Override
        public String resolveAlias(String alias) {
            return delegate.resolveAlias(alias);
        }

        @Override
        public GeneratedAssemblyLine getBoundAssemblyLine(String internalLoaderId) {
            return delegate.getBoundAssemblyLine(internalLoaderId);
        }
    }
}
