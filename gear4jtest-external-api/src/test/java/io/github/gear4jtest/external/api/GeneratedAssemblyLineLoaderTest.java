package io.github.gear4jtest.external.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.artifact.ArtifactIntegrityException;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.InMemoryArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import io.github.gear4jtest.external.api.exception.CompilationTimeoutException;
import io.github.gear4jtest.external.api.exception.GeneratedAssemblyLineLoadTimeoutException;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private static final String BLOCKING_CONSTRUCTOR_CLASS = "io.github.gear4jtest.generated.BlockingConstructorGenerated";
    private static final String BLOCKING_CONSTRUCTOR_SOURCE = """
            package io.github.gear4jtest.generated;

            public final class BlockingConstructorGenerated
                    implements io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine {
                public BlockingConstructorGenerated() {
                    io.github.gear4jtest.external.api.GeneratedLoadingTestHooks.awaitInConstructor();
                }

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
            assertThat(fixture.loader().snapshotStats().phaseStats())
                    .allSatisfy((phase, stats) -> {
                        assertThat(stats.attempts()).as(phase.name()).isEqualTo(1L);
                        assertThat(stats.failures()).as(phase.name()).isZero();
                        assertThat(stats.totalDurationNanos()).as(phase.name()).isNotNegative();
                    });
        } finally {
            releaseCompiler.countDown();
            callers.shutdownNow();
            fixture.loader().close();
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

        try {
            // When / Then
            assertThatThrownBy(() -> fixture.loader().loadOrCompile("line", fixture.object()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("temporary compiler failure");
            assertThat(fixture.loader().loadOrCompile("line", fixture.object())).isNotNull();
            assertThat(compilationCount).hasValue(2);
        } finally {
            fixture.loader().close();
        }
    }

    @Test
    void loadOrCompile_shouldRejectSameSizeCorruptionBeforeTranslation() throws Exception {
        // Given
        byte[] expected = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        byte[] corrupt = expected.clone();
        corrupt[corrupt.length - 2] ^= 1;
        String expectedHash = ArtifactHashes.sha256Hex(expected);
        ArtifactStore artifactStore = mock(ArtifactStore.class);
        when(artifactStore.get(expectedHash)).thenReturn(Optional.of(new Artifact(expectedHash, corrupt.length,
                Map.of(), () -> new ByteArrayInputStream(corrupt))));
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.MEMORY, Map.of());
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        when(storeProvider.forConfig(config)).thenReturn(artifactStore);
        OperationChainTranslatorResolver translatorResolver = mock(OperationChainTranslatorResolver.class);
        GeneratedSourceCompiler compiler = mock(GeneratedSourceCompiler.class);
        GeneratedAssemblyLineLoader loader = new GeneratedAssemblyLineLoader(
                new AssemblyLineStoreResolver(configRepository, storeProvider),
                InMemoryClassLoaderRegistry.builder().build(), translatorResolver, compiler,
                new SimpleDependencyInjector(), getClass().getClassLoader(),
                ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
        OperationChainObject object = object("line", "1.0.0", expectedHash, expected.length);

        try {
            // When / Then
            assertThatThrownBy(() -> loader.loadOrCompile("line", object))
                    .isInstanceOf(ArtifactIntegrityException.class)
                    .hasMessageContaining("content hash mismatch")
                    .hasMessageContaining(expectedHash);
            verifyNoInteractions(translatorResolver, compiler);
            assertThat(loader.snapshotStats().artifactIntegrityFailures()).isEqualTo(1L);
            assertThat(loader.snapshotStats().phase(GeneratedLoadingPhase.ARTIFACT_READ).failures()).isEqualTo(1L);
            assertThat(loader.snapshotStats().phase(GeneratedLoadingPhase.TRANSLATION).attempts()).isZero();
        } finally {
            loader.close();
        }
    }

    @Test
    void loadOrCompile_shouldKeepLegacyDelimiterCollisionCandidatesInSeparateCacheEntries() throws Exception {
        // Given
        Map<String, byte[]> compiledClasses = compileGeneratedClass();
        AtomicInteger compilationCount = new AtomicInteger();
        GeneratedSourceCompiler compiler = (className, sourceCode) -> {
            compilationCount.incrementAndGet();
            return compiledClasses;
        };
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        byte[] artifactBytes = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        String hash = artifactStore.put(artifactBytes);
        OperationChainObject first = object("a:b", "c", hash, artifactBytes.length);
        OperationChainObject second = object("a", "b:c", hash, artifactBytes.length);
        OperationChainConfig firstConfig = new OperationChainConfig(first.alId(), false, StoreType.MEMORY, Map.of());
        OperationChainConfig secondConfig = new OperationChainConfig(second.alId(), false, StoreType.MEMORY, Map.of());
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId(first.alId())).thenReturn(Optional.of(firstConfig));
        when(configRepository.findByAssemblyLineId(second.alId())).thenReturn(Optional.of(secondConfig));
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        when(storeProvider.forConfig(firstConfig)).thenReturn(artifactStore);
        when(storeProvider.forConfig(secondConfig)).thenReturn(artifactStore);
        OperationChainTranslator translator = mock(OperationChainTranslator.class);
        when(translator.translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.TEST)))
                .thenReturn(new OperationChainTranslator.GenerationResult(GENERATED_CLASS, GENERATED_SOURCE));
        OperationChainTranslatorResolver translatorResolver = mock(OperationChainTranslatorResolver.class);
        when(translatorResolver.resolve("application/xml")).thenReturn(translator);
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();
        GeneratedAssemblyLineLoader loader = new GeneratedAssemblyLineLoader(
                new AssemblyLineStoreResolver(configRepository, storeProvider), registry, translatorResolver, compiler,
                new SimpleDependencyInjector(), getClass().getClassLoader(),
                ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);

        try {
            // When
            GeneratedAssemblyLine<?, ?> firstLoaded = loader.loadOrCompile(first.alId(), first);
            GeneratedAssemblyLine<?, ?> secondLoaded = loader.loadOrCompile(second.alId(), second);

            // Then
            assertThat(firstLoaded).isNotSameAs(secondLoaded);
            assertThat(compilationCount).hasValue(2);
            assertThat(registry.snapshotStats().cachedLoaders()).isEqualTo(2);
        } finally {
            loader.close();
        }
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
            fixture.loader().close();
        } finally {
            releaseCompiler.countDown();
            callers.shutdownNow();
        }
    }

    @Test
    void loadOrCompile_shouldBoundBlockingTranslationForOwnerAndJoinerAndRejectLateResult() throws Exception {
        // Given
        Map<String, byte[]> compiledClasses = compileGeneratedClass();
        CountDownLatch translatorEntered = new CountDownLatch(1);
        CountDownLatch releaseTranslator = new CountDownLatch(1);
        AtomicInteger compilationCount = new AtomicInteger();
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        byte[] artifactBytes = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        String hash = artifactStore.put(artifactBytes);
        OperationChainObject object = object("line", "1.0.0", hash, artifactBytes.length);
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.MEMORY, Map.of());
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        ArtifactStoreProvider storeProvider = mock(ArtifactStoreProvider.class);
        when(storeProvider.forConfig(config)).thenReturn(artifactStore);
        OperationChainTranslator translator = new OperationChainTranslator() {
            @Override
            public boolean supports(String mediaType) {
                return "application/xml".equals(mediaType);
            }

            @Override
            public GenerationResult translate(byte[] content, String mediaType) {
                translatorEntered.countDown();
                awaitIgnoringInterruption(releaseTranslator);
                return new GenerationResult(GENERATED_CLASS, GENERATED_SOURCE);
            }
        };
        GeneratedSourceCompiler compiler = (className, sourceCode) -> {
            compilationCount.incrementAndGet();
            return compiledClasses;
        };
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();
        var loadingConfiguration = new GeneratedLoadingConfiguration(Duration.ofMillis(250), 1, 2);
        var loader = new GeneratedAssemblyLineLoader(
                new AssemblyLineStoreResolver(configRepository, storeProvider), registry,
                new OperationChainTranslatorResolver(List.of(translator)), compiler,
                new SimpleDependencyInjector(), getClass().getClassLoader(),
                ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES, loadingConfiguration);
        ExecutorService callers = Executors.newFixedThreadPool(2);

        try {
            Future<GeneratedAssemblyLine<?, ?>> owner = callers.submit(() -> loader.loadOrCompile("line", object));
            assertThat(translatorEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<GeneratedAssemblyLine<?, ?>> joiner = callers.submit(() -> loader.loadOrCompile("line", object));
            awaitStats(() -> loader.snapshotStats().singleFlightJoins() == 1L);

            // When / Then
            assertThatThrownBy(() -> owner.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(GeneratedAssemblyLineLoadTimeoutException.class);
            assertThatThrownBy(() -> joiner.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(GeneratedAssemblyLineLoadTimeoutException.class);
            assertThat(compilationCount).hasValue(0);
            assertThat(loader.snapshotStats().timedOutLoads()).isEqualTo(1L);

            releaseTranslator.countDown();
            awaitStats(() -> loader.snapshotStats().activeLoads() == 0);
            assertThat(compilationCount).hasValue(0);
            assertThat(registry.snapshotStats().cachedLoaders()).isZero();

            assertThat(loader.loadOrCompile("line", object)).isNotNull();
            assertThat(compilationCount).hasValue(1);
            assertThat(loader.snapshotStats().successfulLoads()).isEqualTo(1L);
        } finally {
            releaseTranslator.countDown();
            callers.shutdownNow();
            loader.close();
        }
    }

    @Test
    void loadOrCompile_shouldNotRegisterInstanceReturnedAfterDependencyInjectionTimeout() throws Exception {
        // Given
        Map<String, byte[]> compiledClasses = compileGeneratedClass();
        CountDownLatch injectorEntered = new CountDownLatch(1);
        CountDownLatch releaseInjector = new CountDownLatch(1);
        SimpleDependencyInjector injector = new SimpleDependencyInjector() {
            @Override
            public void injectDependencies(Object instance, ExecutionMode mode) {
                injectorEntered.countDown();
                awaitIgnoringInterruption(releaseInjector);
            }
        };
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();
        LoaderFixture base = fixture((className, sourceCode) -> compiledClasses, registry);
        var loadingConfiguration = new GeneratedLoadingConfiguration(Duration.ofMillis(150), 1, 1);
        var loader = new GeneratedAssemblyLineLoader(base.storeResolver(), registry,
                base.translatorResolver(), (className, sourceCode) -> compiledClasses, injector,
                getClass().getClassLoader(), ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
                loadingConfiguration);
        ExecutorService caller = Executors.newSingleThreadExecutor();

        try {
            Future<GeneratedAssemblyLine<?, ?>> result = caller
                    .submit(() -> loader.loadOrCompile("line", base.object()));
            assertThat(injectorEntered.await(2, TimeUnit.SECONDS)).isTrue();

            // When / Then
            assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(GeneratedAssemblyLineLoadTimeoutException.class);
            releaseInjector.countDown();
            awaitStats(() -> loader.snapshotStats().activeLoads() == 0);
            assertThat(registry.snapshotStats().cachedLoaders()).isZero();
        } finally {
            releaseInjector.countDown();
            caller.shutdownNow();
            loader.close();
            base.loader().close();
        }
    }

    @Test
    void loadOrCompile_shouldNotRegisterInstanceConstructedAfterDeadline() throws Exception {
        // Given
        CountDownLatch constructorEntered = new CountDownLatch(1);
        CountDownLatch releaseConstructor = new CountDownLatch(1);
        GeneratedLoadingTestHooks.installConstructorBlock(constructorEntered, releaseConstructor);
        Map<String, byte[]> compiledClasses = new JavaxToolsGeneratedSourceCompiler(getClass().getClassLoader())
                .compile(BLOCKING_CONSTRUCTOR_CLASS,
                         BLOCKING_CONSTRUCTOR_SOURCE.getBytes(StandardCharsets.UTF_8));
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        byte[] artifactBytes = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        String hash = artifactStore.put(artifactBytes);
        OperationChainObject object = object("line", "1.0.0", hash, artifactBytes.length);
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.MEMORY, Map.of());
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId("line")).thenReturn(Optional.of(config));
        OperationChainTranslator translator = mock(OperationChainTranslator.class);
        when(translator.translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.TEST)))
                .thenReturn(new OperationChainTranslator.GenerationResult(BLOCKING_CONSTRUCTOR_CLASS,
                        BLOCKING_CONSTRUCTOR_SOURCE));
        OperationChainTranslatorResolver translatorResolver = mock(OperationChainTranslatorResolver.class);
        when(translatorResolver.resolve("application/xml")).thenReturn(translator);
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();
        var loader = new GeneratedAssemblyLineLoader(
                new AssemblyLineStoreResolver(configRepository, ignored -> artifactStore), registry,
                translatorResolver, (className, sourceCode) -> compiledClasses,
                new SimpleDependencyInjector(), getClass().getClassLoader(),
                ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
                new GeneratedLoadingConfiguration(Duration.ofMillis(150), 1, 1));
        ExecutorService caller = Executors.newSingleThreadExecutor();

        try {
            Future<GeneratedAssemblyLine<?, ?>> result = caller.submit(() -> loader.loadOrCompile("line", object));
            assertThat(constructorEntered.await(2, TimeUnit.SECONDS)).isTrue();

            // When / Then
            assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .hasCauseInstanceOf(GeneratedAssemblyLineLoadTimeoutException.class);
            releaseConstructor.countDown();
            awaitStats(() -> loader.snapshotStats().activeLoads() == 0);
            assertThat(registry.snapshotStats().cachedLoaders()).isZero();
        } finally {
            releaseConstructor.countDown();
            caller.shutdownNow();
            loader.close();
            GeneratedLoadingTestHooks.clearConstructorBlock();
        }
    }

    @Test
    void loadOrCompile_shouldRejectDistinctLoadWhenBoundedQueueIsFull() throws Exception {
        // Given
        Map<String, byte[]> compiledClasses = compileGeneratedClass();
        CountDownLatch translatorEntered = new CountDownLatch(1);
        CountDownLatch releaseTranslator = new CountDownLatch(1);
        InMemoryArtifactStore artifactStore = new InMemoryArtifactStore();
        byte[] artifactBytes = "<pipeline/>".getBytes(StandardCharsets.UTF_8);
        String hash = artifactStore.put(artifactBytes);
        OperationChainConfig config = new OperationChainConfig("line", false, StoreType.MEMORY, Map.of());
        OperationChainConfigRepository configRepository = mock(OperationChainConfigRepository.class);
        when(configRepository.findByAssemblyLineId(any(String.class))).thenReturn(Optional.of(config));
        ArtifactStoreProvider storeProvider = ignored -> artifactStore;
        OperationChainTranslator translator = new OperationChainTranslator() {
            @Override
            public boolean supports(String mediaType) {
                return true;
            }

            @Override
            public GenerationResult translate(byte[] content, String mediaType) {
                translatorEntered.countDown();
                awaitIgnoringInterruption(releaseTranslator);
                return new GenerationResult(GENERATED_CLASS, GENERATED_SOURCE);
            }
        };
        InMemoryClassLoaderRegistry registry = InMemoryClassLoaderRegistry.builder().build();
        var loader = new GeneratedAssemblyLineLoader(
                new AssemblyLineStoreResolver(configRepository, storeProvider), registry,
                new OperationChainTranslatorResolver(List.of(translator)),
                (className, sourceCode) -> compiledClasses, new SimpleDependencyInjector(),
                getClass().getClassLoader(), ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES,
                new GeneratedLoadingConfiguration(Duration.ofSeconds(5), 1, 1));
        ExecutorService callers = Executors.newFixedThreadPool(2);
        OperationChainObject first = object("line-1", "1.0.0", hash, artifactBytes.length);
        OperationChainObject second = object("line-2", "1.0.0", hash, artifactBytes.length);
        OperationChainObject rejected = object("line-3", "1.0.0", hash, artifactBytes.length);

        try {
            Future<GeneratedAssemblyLine<?, ?>> firstResult = callers
                    .submit(() -> loader.loadOrCompile(first.alId(), first));
            assertThat(translatorEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<GeneratedAssemblyLine<?, ?>> secondResult = callers
                    .submit(() -> loader.loadOrCompile(second.alId(), second));
            awaitStats(() -> loader.snapshotStats().queuedLoads() == 1);

            // When / Then
            assertThatThrownBy(() -> loader.loadOrCompile(rejected.alId(), rejected))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("saturated or closed");
            assertThat(loader.snapshotStats().rejectedLoads()).isEqualTo(1L);

            releaseTranslator.countDown();
            assertThat(firstResult.get(2, TimeUnit.SECONDS)).isNotNull();
            assertThat(secondResult.get(2, TimeUnit.SECONDS)).isNotNull();
            assertThat(registry.snapshotStats().cachedLoaders()).isEqualTo(2);
        } finally {
            releaseTranslator.countDown();
            callers.shutdownNow();
            loader.close();
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
        when(translator.translate(any(byte[].class), eq("application/xml"), eq(ExecutionMode.TEST)))
                .thenReturn(new OperationChainTranslator.GenerationResult(GENERATED_CLASS, GENERATED_SOURCE));
        OperationChainTranslatorResolver translatorResolver = mock(OperationChainTranslatorResolver.class);
        when(translatorResolver.resolve("application/xml")).thenReturn(translator);
        AssemblyLineStoreResolver storeResolver = new AssemblyLineStoreResolver(configRepository, storeProvider);
        GeneratedAssemblyLineLoader loader = new GeneratedAssemblyLineLoader(storeResolver, registry,
                translatorResolver, compiler, new SimpleDependencyInjector(),
                GeneratedAssemblyLineLoaderTest.class.getClassLoader(), ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
        return new LoaderFixture(loader, object, storeResolver, translatorResolver);
    }

    private static OperationChainObject object(String assemblyLineId,
                                               String version,
                                               String hash,
                                               long sizeBytes) {
        return new OperationChainObject(null, assemblyLineId, version, ExecutionMode.TEST, hash, sizeBytes,
                "application/xml", Instant.parse("2026-07-13T08:00:00Z"), "tester",
                Instant.parse("2026-07-13T08:00:00Z"));
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
                // Deliberately non-cooperative component used to prove timeout cleanup.
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

    private record LoaderFixture(GeneratedAssemblyLineLoader loader,
                                 OperationChainObject object,
                                 AssemblyLineStoreResolver storeResolver,
                                 OperationChainTranslatorResolver translatorResolver) {}

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
        public void register(String internalLoaderId,
                             ClassLoader loader,
                             GeneratedAssemblyLine bound,
                             long bytecodeWeightBytes) {
            delegate.register(internalLoaderId, loader, bound, bytecodeWeightBytes);
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
