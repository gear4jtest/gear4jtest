package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.DependencyInjector;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;

import static java.util.Objects.requireNonNull;

final class GeneratedAssemblyLineLoader implements AutoCloseable {
    private final AssemblyLineStoreResolver storeResolver;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final OperationChainTranslatorResolver translatorResolver;
    private final GeneratedSourceCompiler compiler;
    private final DependencyInjector dependencyInjector;
    private final ClassLoader generatedClassParent;
    private final long maxArtifactSizeBytes;
    private final GeneratedLoadingRuntime loadingRuntime;

    GeneratedAssemblyLineLoader(AssemblyLineStoreResolver storeResolver,
                                ClassLoaderRegistry classLoaderRegistry,
                                OperationChainTranslatorResolver translatorResolver,
                                GeneratedSourceCompiler compiler,
                                DependencyInjector dependencyInjector,
                                ClassLoader generatedClassParent,
                                long maxArtifactSizeBytes) {
        this(storeResolver, classLoaderRegistry, translatorResolver, compiler, dependencyInjector,
                generatedClassParent, maxArtifactSizeBytes, GeneratedLoadingConfiguration.defaults());
    }

    GeneratedAssemblyLineLoader(AssemblyLineStoreResolver storeResolver,
                                ClassLoaderRegistry classLoaderRegistry,
                                OperationChainTranslatorResolver translatorResolver,
                                GeneratedSourceCompiler compiler,
                                DependencyInjector dependencyInjector,
                                ClassLoader generatedClassParent,
                                long maxArtifactSizeBytes,
                                GeneratedLoadingConfiguration configuration) {
        this.storeResolver = requireNonNull(storeResolver);
        this.classLoaderRegistry = requireNonNull(classLoaderRegistry);
        this.translatorResolver = requireNonNull(translatorResolver);
        this.compiler = requireNonNull(compiler);
        this.dependencyInjector = requireNonNull(dependencyInjector);
        this.generatedClassParent = requireNonNull(generatedClassParent);
        this.maxArtifactSizeBytes = AssemblyLineIdentifiers.requireValidArtifactSize(maxArtifactSizeBytes);
        this.loadingRuntime = new GeneratedLoadingRuntime(configuration);
    }

    GeneratedAssemblyLine<?, ?> loadOrCompile(String alId, OperationChainObject obj) throws IOException {
        String internalLoaderId = AssemblyLineIdentifiers.toInternalLoaderId(obj);
        return loadingRuntime.load(internalLoaderId, new GeneratedLoadingRuntime.LoadingOperation() {
            @Override
            public GeneratedAssemblyLine<?, ?> findCached() {
                return GeneratedAssemblyLineLoader.this.findCached(internalLoaderId);
            }

            @Override
            public GeneratedLoadingRuntime.LoadResult load(GeneratedLoadingRuntime.LoadAttempt attempt)
                    throws IOException {
                return compileCandidate(alId, obj, internalLoaderId, attempt);
            }
        });
    }

    GeneratedLoadingStats snapshotStats() {
        return loadingRuntime.snapshotStats();
    }

    @Override
    public void close() {
        loadingRuntime.close();
    }

    private GeneratedLoadingRuntime.LoadResult compileCandidate(String alId,
                                                                OperationChainObject obj,
                                                                String internalLoaderId,
                                                                GeneratedLoadingRuntime.LoadAttempt attempt)
            throws IOException {
        long phaseStartedNanos = System.nanoTime();
        byte[] bytes;
        try {
            bytes = readArtifact(alId, obj);
        } finally {
            attempt.recordArtifactReadDuration(System.nanoTime() - phaseStartedNanos);
        }
        if (!attempt.continueLoading()) {
            return null;
        }

        String mediaType = AssemblyLineIdentifiers.normalizeMediaType(obj.mimeType());
        OperationChainTranslator.GenerationResult translated;
        phaseStartedNanos = System.nanoTime();
        try {
            translated = translate(alId, obj, bytes, mediaType);
        } finally {
            attempt.recordTranslationDuration(System.nanoTime() - phaseStartedNanos);
        }
        if (!attempt.continueLoading()) {
            return null;
        }

        Map<String, byte[]> compilationResult;
        phaseStartedNanos = System.nanoTime();
        try {
            compilationResult = compiler
                    .compile(translated.className(), translated.formattedSource().getBytes(StandardCharsets.UTF_8));
        } finally {
            attempt.recordCompilationDuration(System.nanoTime() - phaseStartedNanos);
        }
        if (!attempt.continueLoading()) {
            return null;
        }

        phaseStartedNanos = System.nanoTime();
        try {
            InMemoryClassLoader classLoader = new InMemoryClassLoader(generatedClassParent);
            classLoader.addCompiledClasses(compilationResult);
            GeneratedAssemblyLine<?, ?> instance = instantiate(translated.className(), classLoader, obj.mode());
            long bytecodeWeightBytes = classLoader.bytecodeWeightBytes();
            return new GeneratedLoadingRuntime.LoadResult(instance,
                    () -> classLoaderRegistry.register(internalLoaderId, classLoader, instance,
                                                       bytecodeWeightBytes));
        } finally {
            attempt.recordInstantiationDuration(System.nanoTime() - phaseStartedNanos);
        }
    }

    private GeneratedAssemblyLine<?, ?> findCached(String internalLoaderId) {
        var existing = classLoaderRegistry.get(internalLoaderId);
        if (existing == null) {
            return null;
        }
        return classLoaderRegistry.getBoundAssemblyLine(internalLoaderId);
    }

    private OperationChainTranslator.GenerationResult translate(String alId,
                                                                OperationChainObject obj,
                                                                byte[] bytes,
                                                                String mediaType)
            throws IOException {
        OperationChainTranslator translator = translatorResolver.resolve(mediaType);
        try {
            return translator.translate(bytes, mediaType, obj.mode());
        } catch (Exception e) {
            throw new IOException("Translation failed for alId=%s, version=%s, mediaType=%s"
                    .formatted(alId, obj.version(), mediaType), e);
        }
    }

    private byte[] readArtifact(String alId, OperationChainObject obj) throws IOException {
        ArtifactStore store = storeResolver.resolve(alId);
        Artifact artifact = store.get(obj.contentHash())
                .orElseThrow(() -> new IOException("Artifact not found for hash=" + obj.contentHash()));
        String description = "Assembly line artifact " + obj.contentHash();
        ArtifactHashes.requireSha256Match(obj.contentHash(), artifact.hashHex(), description + " metadata");
        if (artifact.size() != obj.sizeBytes()) {
            throw new IOException(description + " metadata size mismatch: expected " + obj.sizeBytes()
                    + " but found " + artifact.size());
        }
        AssemblyLineIdentifiers.requireAllowedArtifactSize(artifact.size(), maxArtifactSizeBytes,
                                                           description);
        try (InputStream in = artifact.openStreamChecked()) {
            byte[] bytes = ArtifactStore.readAllBytes(in, maxArtifactSizeBytes);
            ArtifactHashes.requireContentIdentity(bytes, obj.contentHash(), obj.sizeBytes(), description);
            return bytes;
        }
    }

    private GeneratedAssemblyLine<?, ?> instantiate(String className, ClassLoader classLoader, ExecutionMode mode)
            throws IOException {
        try {
            Class<?> operationChainClass = classLoader.loadClass(className);
            Object rawInstance = operationChainClass.getDeclaredConstructor().newInstance();
            if (!(rawInstance instanceof GeneratedAssemblyLine<?, ?> generated)) {
                throw new IOException("Generated class does not implement GeneratedAssemblyLine: " + className);
            }
            dependencyInjector.injectDependencies(generated, mode);
            return generated;
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException e) {
            throw new IOException("Unable to instantiate generated class: " + className, e);
        } catch (DependencyInjector.InjectionException e) {
            throw new IOException("Unable to inject dependencies into generated class: " + className, e);
        }
    }
}
