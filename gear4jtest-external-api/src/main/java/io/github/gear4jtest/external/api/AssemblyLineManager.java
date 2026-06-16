package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompilers;
import io.github.gear4jtest.external.api.compiler.JDTInMemoryCompiler;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.DependencyInjector;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;
import io.github.gear4jtest.external.api.storage.ArtifactStoreProvider;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;

import static java.util.Objects.requireNonNull;

/**
 * Public facade for external assembly-line publication, promotion and loading.
 *
 * <p>
 * The class intentionally stays small: persistence publication, store
 * resolution, code translation/compilation/classloading and latest-alias
 * handling are delegated to package-private collaborators so their lifecycle
 * and failure modes can evolve independently without expanding the public API
 * surface.
 * </p>
 */
public class AssemblyLineManager {
    public static final long DEFAULT_MAX_ARTIFACT_SIZE_BYTES = ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES;

    private final OperationChainTagRepository chainTagRepo;
    private final AssemblyLineStoreResolver storeResolver;
    private final AssemblyLineAliasService aliasService;
    private final AssemblyLinePublicationService publicationService;
    private final AssemblyLineLookupService lookupService;

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
                               GeneratedSourceCompiler compiler,
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
                               GeneratedSourceCompiler compiler,
                               DependencyInjector dependencyInjector,
                               ClassLoader generatedClassParent,
                               long maxArtifactSizeBytes) {
        ClassLoader parent = generatedClassParent != null ? generatedClassParent : contextClassLoader();
        GeneratedSourceCompiler effectiveCompiler = compiler != null ? compiler : new JDTInMemoryCompiler(parent);
        DependencyInjector effectiveDependencyInjector = dependencyInjector != null ? dependencyInjector
                : new SimpleDependencyInjector();
        long effectiveMaxArtifactSizeBytes = AssemblyLineIdentifiers.requireValidArtifactSize(maxArtifactSizeBytes);

        this.chainTagRepo = requireNonNull(chainTagRepo);
        this.storeResolver = new AssemblyLineStoreResolver(configRepo, storeProvider);
        this.aliasService = new AssemblyLineAliasService(classLoaderRegistry);
        var loader = new GeneratedAssemblyLineLoader(storeResolver, classLoaderRegistry, translatorResolver,
                effectiveCompiler, effectiveDependencyInjector, parent, effectiveMaxArtifactSizeBytes, aliasService);
        this.publicationService = new AssemblyLinePublicationService(configRepo, objectRepo, chainTagRepo,
                storeResolver, aliasService, effectiveMaxArtifactSizeBytes);
        this.lookupService = new AssemblyLineLookupService(objectRepo, loader, aliasService);
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
                resolver, GeneratedSourceCompilers.fromServiceLoader(cl), new SimpleDependencyInjector(), cl);
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
        return publicationService.registerAssemblyLine(alId, version, mode, content, mediaType, tags, createdBy);
    }

    public void promoteTestToRun(String alId, String version, String promotedBy) throws PolicyViolationException {
        publicationService.promoteTestToRun(alId, version, promotedBy);
    }

    public void invalidateLatestRun(String alId) {
        aliasService.invalidateLatestRun(alId);
    }

    public String resolveLatestRunLoaderId(String alId) {
        return aliasService.resolveLatestRunLoaderId(alId);
    }

    public GeneratedAssemblyLine getOperationChain(String alId, String version, ExecutionMode mode) throws IOException {
        return lookupService.getOperationChain(alId, version, mode);
    }

    /**
     * latest lookup is supported for RUN mode only.
     */
    public GeneratedAssemblyLine getOperationChain(String alId, ExecutionMode mode) throws IOException {
        return lookupService.getLatestRun(alId, mode);
    }

    public List<String> findAlIdsByTag(String tag) {
        return chainTagRepo.findAssemblyLineIdsByTag(tag);
    }

    public Set<String> listTags(String alId) {
        return chainTagRepo.listTags(alId);
    }

    public void invalidateStore(String alId) {
        storeResolver.invalidate(alId);
    }

    public static final class PolicyViolationException extends Exception {
        public PolicyViolationException(String message) {
            super(message);
        }
    }
}
