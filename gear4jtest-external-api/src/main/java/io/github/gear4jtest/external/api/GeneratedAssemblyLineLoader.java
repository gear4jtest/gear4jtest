package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.gear4jtest.external.api.artifact.Artifact;
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

final class GeneratedAssemblyLineLoader {
    private final AssemblyLineStoreResolver storeResolver;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final OperationChainTranslatorResolver translatorResolver;
    private final GeneratedSourceCompiler compiler;
    private final DependencyInjector dependencyInjector;
    private final ClassLoader generatedClassParent;
    private final long maxArtifactSizeBytes;
    private final AssemblyLineAliasService aliasService;

    GeneratedAssemblyLineLoader(AssemblyLineStoreResolver storeResolver,
                                ClassLoaderRegistry classLoaderRegistry,
                                OperationChainTranslatorResolver translatorResolver,
                                GeneratedSourceCompiler compiler,
                                DependencyInjector dependencyInjector,
                                ClassLoader generatedClassParent,
                                long maxArtifactSizeBytes,
                                AssemblyLineAliasService aliasService) {
        this.storeResolver = requireNonNull(storeResolver);
        this.classLoaderRegistry = requireNonNull(classLoaderRegistry);
        this.translatorResolver = requireNonNull(translatorResolver);
        this.compiler = requireNonNull(compiler);
        this.dependencyInjector = requireNonNull(dependencyInjector);
        this.generatedClassParent = requireNonNull(generatedClassParent);
        this.maxArtifactSizeBytes = AssemblyLineIdentifiers.requireValidArtifactSize(maxArtifactSizeBytes);
        this.aliasService = requireNonNull(aliasService);
    }

    GeneratedAssemblyLine loadOrCompile(String alId, OperationChainObject obj) throws IOException {
        String internalLoaderId = AssemblyLineIdentifiers.toInternalLoaderId(obj);

        var existing = classLoaderRegistry.get(internalLoaderId);
        if (existing != null) {
            var bound = classLoaderRegistry.getBoundAssemblyLine(internalLoaderId);
            if (bound != null) {
                aliasService.registerLatestAliasIfNeeded(alId, obj, internalLoaderId);
                return bound;
            }
        }

        byte[] bytes = readArtifact(alId, obj);
        String mediaType = AssemblyLineIdentifiers.normalizeMediaType(obj.mimeType());
        OperationChainTranslator.GenerationResult translated = translate(alId, obj, bytes, mediaType);

        Map<String, byte[]> compilationResult = compiler
                .compile(translated.className(), translated.formattedSource().getBytes(StandardCharsets.UTF_8));

        InMemoryClassLoader classLoader = new InMemoryClassLoader(generatedClassParent);
        classLoader.addCompiledClasses(compilationResult);

        GeneratedAssemblyLine instance = instantiate(translated.className(), classLoader, obj.mode());

        classLoaderRegistry.register(internalLoaderId, classLoader, instance);
        aliasService.registerLatestAliasIfNeeded(alId, obj, internalLoaderId);
        return instance;
    }

    private OperationChainTranslator.GenerationResult translate(String alId,
                                                                OperationChainObject obj,
                                                                byte[] bytes,
                                                                String mediaType)
            throws IOException {
        OperationChainTranslator translator = translatorResolver.resolve(mediaType);
        try {
            return translator.translate(bytes, mediaType);
        } catch (Exception e) {
            throw new IOException("Translation failed for alId=%s, version=%s, mediaType=%s"
                    .formatted(alId, obj.version(), mediaType), e);
        }
    }

    private byte[] readArtifact(String alId, OperationChainObject obj) throws IOException {
        ArtifactStore store = storeResolver.resolve(alId);
        Artifact artifact = store.get(obj.contentHash())
                .orElseThrow(() -> new IOException("Artifact not found for hash=" + obj.contentHash()));
        AssemblyLineIdentifiers.requireAllowedArtifactSize(artifact.size(), maxArtifactSizeBytes,
                                                           "Assembly line artifact " + obj.contentHash());
        try (InputStream in = artifact.openStream()) {
            return ArtifactStore.readAllBytes(in, maxArtifactSizeBytes);
        }
    }

    private GeneratedAssemblyLine instantiate(String className, ClassLoader classLoader, ExecutionMode mode)
            throws IOException {
        try {
            Class<?> operationChainClass = classLoader.loadClass(className);
            Object rawInstance = operationChainClass.getDeclaredConstructor().newInstance();
            if (!(rawInstance instanceof GeneratedAssemblyLine generated)) {
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
