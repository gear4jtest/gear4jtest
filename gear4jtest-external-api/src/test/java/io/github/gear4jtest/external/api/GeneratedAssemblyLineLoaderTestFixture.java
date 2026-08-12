package io.github.gear4jtest.external.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.InMemoryArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class GeneratedAssemblyLineLoaderTestFixture {
    private GeneratedAssemblyLineLoaderTestFixture() {
    }

    static final String GENERATED_CLASS = "io.github.gear4jtest.generated.ConcurrentGenerated";
    static final String GENERATED_SOURCE = """
            package io.github.gear4jtest.generated;

            public final class ConcurrentGenerated
                    implements io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine {
                @Override
                public io.github.gear4jtest.core.api.AssemblyLine getAssemblyLineDefinition() {
                    return null;
                }
            }
            """;
    static final String BLOCKING_CONSTRUCTOR_CLASS = "io.github.gear4jtest.generated.BlockingConstructorGenerated";
    static final String BLOCKING_CONSTRUCTOR_SOURCE = """
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

    static LoaderFixture fixture(GeneratedSourceCompiler compiler, ClassLoaderRegistry registry)
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
                GeneratedAssemblyLineLoaderTestFixture.class.getClassLoader(),
                ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
        return new LoaderFixture(loader, object, storeResolver, translatorResolver);
    }

    static OperationChainObject object(String assemblyLineId,
                                       String version,
                                       String hash,
                                       long sizeBytes) {
        return new OperationChainObject(null, assemblyLineId, version, ExecutionMode.TEST, hash, sizeBytes,
                "application/xml", Instant.parse("2026-07-13T08:00:00Z"), "tester",
                Instant.parse("2026-07-13T08:00:00Z"));
    }

    static Map<String, byte[]> compileGeneratedClass() {
        return new JavaxToolsGeneratedSourceCompiler(GeneratedAssemblyLineLoaderTestFixture.class.getClassLoader())
                .compile(GENERATED_CLASS, GENERATED_SOURCE.getBytes(StandardCharsets.UTF_8));
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }

    static void awaitIgnoringInterruption(CountDownLatch latch) {
        boolean released = false;
        while (!released) {
            try {
                released = latch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                // Deliberately non-cooperative component used to prove timeout cleanup.
            }
        }
    }

    static void awaitStats(Condition condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.evaluate() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertThat(condition.evaluate()).isTrue();
    }

    @FunctionalInterface
    interface Condition {
        boolean evaluate();
    }

    record LoaderFixture(GeneratedAssemblyLineLoader loader,
                         OperationChainObject object,
                         AssemblyLineStoreResolver storeResolver,
                         OperationChainTranslatorResolver translatorResolver) {}

    static final class CoordinatedRegistry implements ClassLoaderRegistry {
        final ClassLoaderRegistry delegate = InMemoryClassLoaderRegistry.builder().build();
        final CountDownLatch initialLookups;

        CoordinatedRegistry(int concurrentLookups) {
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
                             long bytecodeWeightBytes,
                             ClassLoaderRegistry.RegistrationLease registrationLease) {
            delegate.register(internalLoaderId, loader, bound, bytecodeWeightBytes, registrationLease);
        }

        @Override
        public void evict(String internalLoaderId) {
            delegate.evict(internalLoaderId);
        }

        @Override
        public boolean evictIfOwned(String internalLoaderId, ClassLoader expectedLoader) {
            return delegate.evictIfOwned(internalLoaderId, expectedLoader);
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

    static final class BlockingFirstRegistrationRegistry implements ClassLoaderRegistry {
        final InMemoryClassLoaderRegistry delegate = InMemoryClassLoaderRegistry.builder().build();
        final CountDownLatch registrationEntered = new CountDownLatch(1);
        final CountDownLatch releaseRegistration = new CountDownLatch(1);
        final AtomicInteger registrations = new AtomicInteger();

        @Override
        public ClassLoader get(String internalLoaderId) {
            return delegate.get(internalLoaderId);
        }

        @Override
        public void register(String internalLoaderId,
                             ClassLoader loader,
                             GeneratedAssemblyLine bound,
                             long bytecodeWeightBytes,
                             ClassLoaderRegistry.RegistrationLease registrationLease) {
            int registration = registrations.incrementAndGet();
            delegate.register(internalLoaderId, loader, bound, bytecodeWeightBytes, registrationLease);
            if (registration == 1) {
                registrationEntered.countDown();
                awaitIgnoringInterruption(releaseRegistration);
            }
        }

        @Override
        public void evict(String internalLoaderId) {
            delegate.evict(internalLoaderId);
        }

        @Override
        public boolean evictIfOwned(String internalLoaderId, ClassLoader expectedLoader) {
            return delegate.evictIfOwned(internalLoaderId, expectedLoader);
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

        InMemoryClassLoaderRegistry.RegistryStats snapshotStats() {
            return delegate.snapshotStats();
        }
    }
}
