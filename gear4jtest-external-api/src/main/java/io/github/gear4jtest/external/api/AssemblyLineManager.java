package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompilers;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.DependencyInjector;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
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

    public static Builder builder() {
        return new Builder();
    }

    private AssemblyLineManager(Builder builder) {
        ClassLoader parent = builder.generatedClassParent != null ? builder.generatedClassParent : contextClassLoader();
        GeneratedSourceCompiler effectiveCompiler = builder.compiler != null ? builder.compiler
                : GeneratedSourceCompilers.defaultCompiler(parent);
        DependencyInjector effectiveDependencyInjector = builder.dependencyInjector != null ? builder.dependencyInjector
                : new SimpleDependencyInjector();
        long effectiveMaxArtifactSizeBytes = AssemblyLineIdentifiers.requireValidArtifactSize(
                                                                                              builder.maxArtifactSizeBytes);

        this.chainTagRepo = requireNonNull(builder.chainTagRepo);
        this.storeResolver = new AssemblyLineStoreResolver(builder.configRepo, builder.storeProvider);
        this.aliasService = new AssemblyLineAliasService(builder.classLoaderRegistry);
        var loader = new GeneratedAssemblyLineLoader(storeResolver, builder.classLoaderRegistry,
                builder.translatorResolver, effectiveCompiler, effectiveDependencyInjector, parent,
                effectiveMaxArtifactSizeBytes);
        var publicationValidator = new AssemblyLinePublicationValidator(storeResolver, builder.translatorResolver,
                effectiveCompiler, effectiveMaxArtifactSizeBytes);
        OperationChainPublicationRepository effectivePublicationRepository = builder.publicationRepository != null
                ? builder.publicationRepository
                : builder.objectRepo instanceof OperationChainPublicationRepository repository ? repository : null;
        this.publicationService = new AssemblyLinePublicationService(builder.configRepo, builder.objectRepo,
                builder.chainTagRepo, effectivePublicationRepository, storeResolver, aliasService, publicationValidator,
                effectiveMaxArtifactSizeBytes);
        this.lookupService = new AssemblyLineLookupService(builder.objectRepo, loader, aliasService);
    }

    public static final class Builder {
        private OperationChainConfigRepository configRepo;
        private OperationChainObjectRepository objectRepo;
        private OperationChainTagRepository chainTagRepo;
        private OperationChainPublicationRepository publicationRepository;
        private ArtifactStoreProvider storeProvider;
        private ClassLoaderRegistry classLoaderRegistry;
        private OperationChainTranslatorResolver translatorResolver;
        private GeneratedSourceCompiler compiler;
        private DependencyInjector dependencyInjector;
        private ClassLoader generatedClassParent;
        private long maxArtifactSizeBytes = DEFAULT_MAX_ARTIFACT_SIZE_BYTES;

        private Builder() {
        }

        public Builder configRepository(OperationChainConfigRepository configRepo) {
            this.configRepo = configRepo;
            return this;
        }

        public Builder objectRepository(OperationChainObjectRepository objectRepo) {
            this.objectRepo = objectRepo;
            return this;
        }

        public Builder tagRepository(OperationChainTagRepository chainTagRepo) {
            this.chainTagRepo = chainTagRepo;
            return this;
        }

        /**
         * Sets the repository responsible for atomic and idempotent publication of
         * object metadata and tags. JDBC object repositories provide this contract
         * directly and are detected automatically.
         */
        public Builder publicationRepository(OperationChainPublicationRepository publicationRepository) {
            this.publicationRepository = publicationRepository;
            return this;
        }

        public Builder storeProvider(ArtifactStoreProvider storeProvider) {
            this.storeProvider = storeProvider;
            return this;
        }

        public Builder classLoaderRegistry(ClassLoaderRegistry classLoaderRegistry) {
            this.classLoaderRegistry = classLoaderRegistry;
            return this;
        }

        public Builder translatorResolver(OperationChainTranslatorResolver translatorResolver) {
            this.translatorResolver = translatorResolver;
            return this;
        }

        public Builder compiler(GeneratedSourceCompiler compiler) {
            this.compiler = compiler;
            return this;
        }

        public Builder dependencyInjector(DependencyInjector dependencyInjector) {
            this.dependencyInjector = dependencyInjector;
            return this;
        }

        public Builder generatedClassParent(ClassLoader generatedClassParent) {
            this.generatedClassParent = generatedClassParent;
            return this;
        }

        public Builder maxArtifactSizeBytes(long maxArtifactSizeBytes) {
            this.maxArtifactSizeBytes = maxArtifactSizeBytes;
            return this;
        }

        public AssemblyLineManager build() {
            return new AssemblyLineManager(this);
        }
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
        return AssemblyLineManager.builder()
                .configRepository(configRepo)
                .objectRepository(objectRepo)
                .tagRepository(chainTagRepo)
                .storeProvider(storeProvider)
                .classLoaderRegistry(classLoaderRegistry)
                .translatorResolver(resolver)
                .compiler(GeneratedSourceCompilers.fromServiceLoader(cl))
                .dependencyInjector(new SimpleDependencyInjector())
                .generatedClassParent(cl)
                .build();
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

    public GeneratedAssemblyLine<?, ?> getOperationChain(String alId, String version, ExecutionMode mode)
            throws IOException {
        return lookupService.getOperationChain(alId, version, mode);
    }

    /**
     * latest lookup is supported for RUN mode only.
     */
    public GeneratedAssemblyLine<?, ?> getOperationChain(String alId, ExecutionMode mode) throws IOException {
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

        public PolicyViolationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
