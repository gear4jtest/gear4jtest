package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.JDTInMemoryCompiler;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.DependencyInjector;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;
import io.github.gear4jtest.external.api.storage.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;

import static java.util.Objects.requireNonNull;

public class AssemblyLineManager {
    public static final long DEFAULT_MAX_ARTIFACT_SIZE_BYTES = ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES;

    private final OperationChainConfigRepository configRepo;
    private final OperationChainObjectRepository objectRepo;
    private final OperationChainTagRepository chainTagRepo;
    private final ArtifactStoreProvider storeProvider;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final OperationChainTranslatorResolver translatorResolver;
    private final JDTInMemoryCompiler compiler;
    private final DependencyInjector dependencyInjector;
    private final ClassLoader generatedClassParent;
    private final Map<String, StoreCacheEntry> storeCacheByAl = new ConcurrentHashMap<>();
    private final long maxArtifactSizeBytes;

    public AssemblyLineManager(OperationChainConfigRepository configRepo,
                               OperationChainObjectRepository objectRepo,
                               OperationChainTagRepository chainTagRepo,
                               ArtifactStoreProvider storeProvider,
                               ClassLoaderRegistry classLoaderRegistry,
                               OperationChainTranslatorResolver translatorResolver) {
        this(configRepo, objectRepo, chainTagRepo, storeProvider, classLoaderRegistry, translatorResolver,
                new JDTInMemoryCompiler(contextClassLoader()), new SimpleDependencyInjector(), contextClassLoader(),
                DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
    }

    public AssemblyLineManager(OperationChainConfigRepository configRepo,
                               OperationChainObjectRepository objectRepo,
                               OperationChainTagRepository chainTagRepo,
                               ArtifactStoreProvider storeProvider,
                               ClassLoaderRegistry classLoaderRegistry,
                               OperationChainTranslatorResolver translatorResolver,
                               JDTInMemoryCompiler compiler,
                               DependencyInjector dependencyInjector,
                               ClassLoader generatedClassParent) {
        this(configRepo, objectRepo, chainTagRepo, storeProvider, classLoaderRegistry, translatorResolver, compiler,
                dependencyInjector, generatedClassParent, DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
    }

    public AssemblyLineManager(OperationChainConfigRepository configRepo,
                               OperationChainObjectRepository objectRepo,
                               OperationChainTagRepository chainTagRepo,
                               ArtifactStoreProvider storeProvider,
                               ClassLoaderRegistry classLoaderRegistry,
                               OperationChainTranslatorResolver translatorResolver,
                               JDTInMemoryCompiler compiler,
                               DependencyInjector dependencyInjector,
                               ClassLoader generatedClassParent,
                               long maxArtifactSizeBytes) {
        this.configRepo = requireNonNull(configRepo);
        this.objectRepo = requireNonNull(objectRepo);
        this.chainTagRepo = requireNonNull(chainTagRepo);
        this.storeProvider = requireNonNull(storeProvider);
        this.classLoaderRegistry = requireNonNull(classLoaderRegistry);
        this.translatorResolver = requireNonNull(translatorResolver);
        this.generatedClassParent = generatedClassParent != null ? generatedClassParent : contextClassLoader();
        this.compiler = compiler != null ? compiler : new JDTInMemoryCompiler(this.generatedClassParent);
        this.dependencyInjector = dependencyInjector != null ? dependencyInjector : new SimpleDependencyInjector();
        this.maxArtifactSizeBytes = requireValidArtifactSize(maxArtifactSizeBytes);
    }

    /**
     * Discovery variant using {@link java.util.ServiceLoader} on the current thread
     * context classloader.
     */
    public static AssemblyLineManager withServiceLoadedCompilers(OperationChainConfigRepository configRepo,
                                                                 OperationChainObjectRepository objectRepo,
                                                                 OperationChainTagRepository chainTagRepo,
                                                                 ArtifactStoreProvider storeProvider,
                                                                 ClassLoaderRegistry classLoaderRegistry) {
        ClassLoader cl = contextClassLoader();
        var resolver = OperationChainTranslatorResolver.fromServiceLoader(cl);
        return new AssemblyLineManager(configRepo, objectRepo, chainTagRepo, storeProvider, classLoaderRegistry,
                resolver, new JDTInMemoryCompiler(cl), new SimpleDependencyInjector(), cl);
    }

    private static String normalizeMediaType(String mediaType) {
        return (mediaType == null || mediaType.isBlank()) ? "application/xml" : mediaType;
    }

    private static long requireValidArtifactSize(long maxArtifactSizeBytes) {
        if (maxArtifactSizeBytes < ArtifactStore.UNLIMITED_SIZE) {
            throw new IllegalArgumentException("maxArtifactSizeBytes must be -1 or >= 0");
        }
        return maxArtifactSizeBytes;
    }

    private void requireAllowedArtifactSize(long sizeBytes, String description) throws IOException {
        if (maxArtifactSizeBytes >= 0 && sizeBytes > maxArtifactSizeBytes) {
            throw new IOException(description + " exceeds configured maxArtifactSizeBytes=" + maxArtifactSizeBytes
                    + ". actualSizeBytes=" + sizeBytes);
        }
    }

    private static String toInternalLoaderId(OperationChainObject obj) {
        return obj.alId() + ":" + obj.version() + ":" + obj.mode() + ":" + obj.contentHash();
    }

    private static String latestAlias(String alId) {
        return "al/" + alId + "/RUN/latest";
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : ClassLoader.getSystemClassLoader();
    }

    public String registerAssemblyLine(String alId,
                                       String version,
                                       ExecutionMode mode,
                                       byte[] content,
                                       String mediaType,
                                       List<String> tags,
                                       String createdBy)
            throws IOException, PolicyViolationException {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required for persisted TEST/RUN publication");
        }
        requireNonNull(alId);
        requireNonNull(mode);
        requireNonNull(content);

        if (mode == ExecutionMode.RUN) {
            OperationChainConfig cfg = configRepo.findByAssemblyLineId(alId)
                    .orElseThrow(() -> new NoSuchElementException("Config not found for alId=" + alId));
            if (!Boolean.TRUE.equals(cfg.allowRunPublicationWithoutTest())) {
                throw new PolicyViolationException("Direct RUN publication is disabled for alId=" + alId);
            }
        }

        requireAllowedArtifactSize(content.length, "Assembly line artifact");
        ArtifactStore store = resolveStoreForAl(alId);
        String hash = store.put(content);

        OperationChainObject obj = new OperationChainObject(null, alId, version, mode, hash, content.length,
                normalizeMediaType(mediaType), Instant.now(), createdBy, Instant.now());
        objectRepo.insert(obj);
        if (mode == ExecutionMode.RUN) {
            invalidateLatestRun(alId);
        }

        if (tags != null && !tags.isEmpty()) {
            for (String tag : tags) {
                chainTagRepo.addTag(alId, tag);
            }
        }
        return hash;
    }

    public void promoteTestToRun(String alId, String version, String promotedBy) throws PolicyViolationException {
        var testObj = objectRepo.find(alId, version, ExecutionMode.TEST).orElseThrow(() -> new NoSuchElementException(
                "TEST object not found for %s:%s".formatted(alId, version)));
        if (objectRepo.exists(alId, version, ExecutionMode.RUN)) {
            var runObj = objectRepo.find(alId, version, ExecutionMode.RUN).orElseThrow();
            if (!Objects.equals(runObj.contentHash(), testObj.contentHash())) {
                throw new PolicyViolationException("RUN object already exists with different content_hash");
            }
            return;
        }
        var runObj = new OperationChainObject(null, alId, version, ExecutionMode.RUN, testObj.contentHash(),
                testObj.sizeBytes(), testObj.mimeType(), Instant.now(), promotedBy, Instant.now());
        objectRepo.insert(runObj);
        invalidateLatestRun(alId);
    }

    public void invalidateLatestRun(String alId) {
        classLoaderRegistry.clearAlias(latestAlias(requireNonNull(alId)));
    }

    public String resolveLatestRunLoaderId(String alId) {
        return classLoaderRegistry.resolveAlias(latestAlias(requireNonNull(alId)));
    }

    private void clearLatestAliasIfResolutionChanged(String alId, String resolvedLoaderId) {
        String alias = latestAlias(alId);
        String current = classLoaderRegistry.resolveAlias(alias);
        if (current != null && !current.equals(resolvedLoaderId)) {
            classLoaderRegistry.clearAlias(alias);
        }
    }

    public GeneratedAssemblyLine getOperationChain(String alId, String version, ExecutionMode mode) throws IOException {
        var obj = objectRepo.find(alId, version, mode).orElseThrow(() -> new NoSuchElementException(
                "Object not found for %s:%s:%s".formatted(alId, version, mode)));
        return loadOrCompile(alId, obj);
    }

    /**
     * latest lookup is supported for RUN mode only.
     */
    public GeneratedAssemblyLine getOperationChain(String alId, ExecutionMode mode) throws IOException {
        if (mode != ExecutionMode.RUN) {
            throw new IllegalArgumentException("Latest is only supported for RUN mode");
        }
        var latest = objectRepo.findLatestRun(alId)
                .orElseThrow(() -> new NoSuchElementException("No RUN object found for alId=" + alId));
        clearLatestAliasIfResolutionChanged(alId, toInternalLoaderId(latest));
        return loadOrCompile(alId, latest);
    }

    public List<String> findAlIdsByTag(String tag) {
        return chainTagRepo.findAssemblyLineIdsByTag(tag);
    }

    public Set<String> listTags(String alId) {
        return chainTagRepo.listTags(alId);
    }

    public void invalidateStore(String alId) {
        storeCacheByAl.remove(alId);
    }

    private ArtifactStore resolveStoreForAl(String alId) {
        var cfg = configRepo.findByAssemblyLineId(alId)
                .orElseThrow(() -> new NoSuchElementException("Config not found for alId=" + alId));
        StoreFingerprint fingerprint = StoreFingerprint.from(cfg);
        StoreCacheEntry cached = storeCacheByAl.get(alId);
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.store();
        }

        ArtifactStore store = storeProvider.forConfig(cfg);
        storeCacheByAl.put(alId, new StoreCacheEntry(fingerprint, store));
        return store;
    }

    private GeneratedAssemblyLine loadOrCompile(String alId, OperationChainObject obj) throws IOException {
        String internalLoaderId = toInternalLoaderId(obj);

        var existing = classLoaderRegistry.get(internalLoaderId);
        if (existing != null) {
            var bound = classLoaderRegistry.getBoundAssemblyLine(internalLoaderId);
            if (bound != null) {
                registerLatestAliasIfNeeded(alId, obj, internalLoaderId);
                return bound;
            }
        }

        byte[] bytes = readArtifact(alId, obj);
        String mediaType = normalizeMediaType(obj.mimeType());

        OperationChainTranslator translator = translatorResolver.resolve(mediaType);
        OperationChainTranslator.GenerationResult translated;
        try {
            translated = translator.translate(bytes, mediaType);
        } catch (Exception e) {
            throw new IOException("Translation failed for alId=%s, version=%s, mediaType=%s"
                    .formatted(alId, obj.version(), mediaType), e);
        }

        Map<String, byte[]> compilationResult = compiler
                .compile(translated.className(), translated.formattedSource().getBytes(StandardCharsets.UTF_8));

        InMemoryClassLoader classLoader = new InMemoryClassLoader(generatedClassParent);
        classLoader.addCompiledClasses(compilationResult);

        GeneratedAssemblyLine instance = instantiate(translated.className(), classLoader, obj.mode());

        classLoaderRegistry.register(internalLoaderId, classLoader, instance);
        registerLatestAliasIfNeeded(alId, obj, internalLoaderId);
        return instance;
    }

    private byte[] readArtifact(String alId, OperationChainObject obj) throws IOException {
        ArtifactStore store = resolveStoreForAl(alId);
        Artifact art = store.get(obj.contentHash())
                .orElseThrow(() -> new IOException("Artifact not found for hash=" + obj.contentHash()));
        requireAllowedArtifactSize(art.size(), "Assembly line artifact " + obj.contentHash());
        try (InputStream in = art.openStream()) {
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

    private void registerLatestAliasIfNeeded(String alId, OperationChainObject obj, String internalLoaderId) {
        if (obj.mode() == ExecutionMode.RUN) {
            classLoaderRegistry.setAlias(latestAlias(alId), internalLoaderId);
        }
    }

    private record StoreCacheEntry(StoreFingerprint fingerprint, ArtifactStore store) {}

    private record StoreFingerprint(StoreType storeType, Map<String, String> storeProps) {
        private static StoreFingerprint from(OperationChainConfig config) {
            return new StoreFingerprint(config.storeType(), Map.copyOf(config.storeProps()));
        }
    }

    public static final class PolicyViolationException extends Exception {
        public PolicyViolationException(String message) {
            super(message);
        }
    }
}
