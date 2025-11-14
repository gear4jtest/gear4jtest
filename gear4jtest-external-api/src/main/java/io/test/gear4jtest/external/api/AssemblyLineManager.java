package io.test.gear4jtest.external.api;

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

import io.test.gear4jtest.external.api.artifact.Artifact;
import io.test.gear4jtest.external.api.artifact.ArtifactStore;
import io.test.gear4jtest.external.api.compiler.JDTInMemoryCompiler;
import io.test.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.test.gear4jtest.external.api.loader.DependencyInjector;
import io.test.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.test.gear4jtest.external.api.loader.InMemoryClassLoader;
import io.test.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.test.gear4jtest.external.api.model.OperationChainConfig;
import io.test.gear4jtest.external.api.model.OperationChainObject;
import io.test.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.test.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.test.gear4jtest.external.api.repository.OperationChainTagRepository;
import io.test.gear4jtest.external.api.storage.ArtifactStoreProvider;
import io.test.gear4jtest.external.api.translator.OperationChainTranslator;
import io.test.gear4jtest.external.api.translator.OperationChainTranslatorResolver;

import static java.util.Objects.requireNonNull;

public class AssemblyLineManager {

    private final OperationChainConfigRepository configRepo;
    private final OperationChainObjectRepository objectRepo;
    private final OperationChainTagRepository chainTagRepo;
    private final ArtifactStoreProvider storeProvider;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final OperationChainTranslatorResolver translatorResolver;
    private final JDTInMemoryCompiler compiler;
    private final DependencyInjector dependencyInjector;

    private final Map<String, ArtifactStore> storeCacheByAl = new ConcurrentHashMap<>();

    public AssemblyLineManager(OperationChainConfigRepository configRepo,
                               OperationChainObjectRepository objectRepo,
                               OperationChainTagRepository chainTagRepo,
                               ArtifactStoreProvider storeProvider,
                               ClassLoaderRegistry classLoaderRegistry,
                               OperationChainTranslatorResolver translatorResolver) {
        this.configRepo = requireNonNull(configRepo);
        this.objectRepo = requireNonNull(objectRepo);
        this.chainTagRepo = requireNonNull(chainTagRepo);
        this.storeProvider = requireNonNull(storeProvider);
        this.classLoaderRegistry = requireNonNull(classLoaderRegistry);
        this.translatorResolver = requireNonNull(translatorResolver);
        this.compiler = new JDTInMemoryCompiler();
        this.dependencyInjector = new SimpleDependencyInjector();
    }

    /** Variante discovery via ServiceLoader sur le TCCL */
    public static AssemblyLineManager withServiceLoadedCompilers(OperationChainConfigRepository configRepo,
                                                                 OperationChainObjectRepository objectRepo,
                                                                 OperationChainTagRepository chainTagRepo,
                                                                 ArtifactStoreProvider storeProvider,
                                                                 ClassLoaderRegistry classLoaderRegistry) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        var resolver = OperationChainTranslatorResolver.fromServiceLoader(cl);
        return new AssemblyLineManager(configRepo, objectRepo, chainTagRepo, storeProvider, classLoaderRegistry, resolver);
    }

    // ----------------- API -----------------

    public String registerAssemblyLine(String alId, String version, ExecutionMode mode,
                                       byte[] content, String mediaType,
                                       List<String> tags, String createdBy)
            throws IOException, PolicyViolationException {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required for persisted TEST/RUN publication");
        }
        requireNonNull(alId); requireNonNull(version); requireNonNull(mode); requireNonNull(content);


        if (mode == ExecutionMode.RUN) {
            OperationChainConfig cfg = configRepo.findByAssemblyLineId(alId)
                    .orElseThrow(() -> new NoSuchElementException("Config not found for alId=" + alId));
            if (!Boolean.TRUE.equals(cfg.allowRunPublicationWithoutTest())) {
                throw new PolicyViolationException("Direct RUN publication is disabled for alId=" + alId);
            }
        }

        ArtifactStore store = resolveStoreForAl(alId);
        String hash = store.put(content);

        OperationChainObject obj = new OperationChainObject(
                null, alId, version, mode, hash, content.length,
                (mediaType == null || mediaType.isBlank()) ? "application/xml" : mediaType,
                Instant.now(), createdBy, Instant.now()
        );
        objectRepo.insert(obj);

        if (tags != null && !tags.isEmpty()) {
            for (String t : tags) chainTagRepo.addTag(alId, t); // tags sur l’AL uniquement
        }
        return hash;
    }

    public void promoteTestToRun(String alId, String version, String promotedBy) throws PolicyViolationException {
        var testObj = objectRepo.find(alId, version, ExecutionMode.TEST)
                .orElseThrow(() -> new NoSuchElementException("TEST object not found for %s:%s".formatted(alId, version)));
        if (objectRepo.exists(alId, version, ExecutionMode.RUN)) {
            var runObj = objectRepo.find(alId, version, ExecutionMode.RUN).orElseThrow();
            if (!Objects.equals(runObj.contentHash(), testObj.contentHash()))
                throw new PolicyViolationException("RUN object already exists with different content_hash");
            return;
        }
        var runObj = new OperationChainObject(null, alId, version, ExecutionMode.RUN, testObj.contentHash(),
                testObj.sizeBytes(), testObj.mimeType(), Instant.now(), promotedBy, Instant.now());
        objectRepo.insert(runObj);
    }

    public GeneratedAssemblyLine getOperationChain(String alId, String version, ExecutionMode mode) throws IOException {
        var obj = objectRepo.find(alId, version, mode)
                .orElseThrow(() -> new NoSuchElementException("Object not found for %s:%s:%s".formatted(alId, version, mode)));
        return loadOrCompile(alId, obj);
    }

    /** latest (RUN only) */
    public GeneratedAssemblyLine getOperationChain(String alId, ExecutionMode mode) throws IOException {
        if (mode != ExecutionMode.RUN) throw new IllegalArgumentException("Latest is only supported for RUN mode");
        var latest = objectRepo.findLatestRun(alId)
                .orElseThrow(() -> new NoSuchElementException("No RUN object found for alId=" + alId));
        return loadOrCompile(alId, latest);
    }

    public List<String> findAlIdsByTag(String tag) {
        return chainTagRepo.findAssemblyLineIdsByTag(tag);
    }

    public Set<String> listTags(String alId) {
        return chainTagRepo.listTags(alId);
    }

    public void invalidateStore(String alId) { storeCacheByAl.remove(alId); }

    // ----------------- internals -----------------

    private ArtifactStore resolveStoreForAl(String alId) {
        return storeCacheByAl.computeIfAbsent(alId, id -> {
            var cfg = configRepo.findByAssemblyLineId(id).orElseThrow(() -> new NoSuchElementException("Config not found for alId=" + id));
            return storeProvider.forConfig(cfg);
        });
    }

    private GeneratedAssemblyLine loadOrCompile(String alId, OperationChainObject obj) throws IOException {
        String internalLoaderId = toInternalLoaderId(obj);

        var existing = classLoaderRegistry.get(internalLoaderId);
        if (existing != null) {
            var bound = classLoaderRegistry.getBoundAssemblyLine(internalLoaderId);
            if (bound != null) {
                if (obj.mode() == ExecutionMode.RUN) classLoaderRegistry.setAlias(latestAlias(alId), internalLoaderId);
                return bound;
            }
        }

        ArtifactStore store = resolveStoreForAl(alId);
        Artifact art = store.get(obj.contentHash())
                .orElseThrow(() -> new IOException("Artifact not found for hash=" + obj.contentHash()));
        byte[] bytes;
        try (InputStream in = art.openStream()) { bytes = in.readAllBytes(); }

        String mediaType = (obj.mimeType() == null || obj.mimeType().isBlank()) ? "application/xml" : obj.mimeType();
        OperationChainTranslator translator = translatorResolver.resolve(mediaType);
        OperationChainTranslator.GenerationResult translated;
        try {
            translated = translator.translate(bytes, mediaType);
        } catch (Exception e) {
            throw new IOException("Compilation failed for alId=" + alId + ", version=" + obj.version() + ", mediaType=" + mediaType, e);
        }
        Map<String, byte[]> compilationResult = compiler.compile(translated.className(), translated.formattedSource().getBytes(StandardCharsets.UTF_8));

        // 3. Chargement de la classe
        InMemoryClassLoader classLoader = new InMemoryClassLoader();
        classLoader.addCompiledClasses(compilationResult);
        Class<?> operationChainClass;
        try {
            operationChainClass = classLoader.loadClass(translated.className());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // 4. Création de l'instance
        GeneratedAssemblyLine rawInstance;
        try {
            rawInstance = (GeneratedAssemblyLine) operationChainClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        // 5. Injection des dépendances
        try {
            dependencyInjector.injectDependencies(rawInstance, null);
        } catch (DependencyInjector.InjectionException e) {
            throw new RuntimeException(e);
        }

        // 6. Sauvegarde du ClassLoader pour nettoyage ultérieur
        classLoaderRegistry.register(internalLoaderId, classLoader, rawInstance);
        if (obj.mode() == ExecutionMode.RUN) {
            classLoaderRegistry.setAlias(latestAlias(alId), internalLoaderId);
        }
        return rawInstance;
    }

    private static String toInternalLoaderId(OperationChainObject obj) {
        return obj.alId() + ":" + obj.version() + ":" + obj.mode() + ":" + obj.contentHash();
    }
    private static String latestAlias(String alId) { return "al/" + alId + "/RUN/latest"; }

    public static final class PolicyViolationException extends Exception {
        public PolicyViolationException(String m) { super(m); }
    }
}
