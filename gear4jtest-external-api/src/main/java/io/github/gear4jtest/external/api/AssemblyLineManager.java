package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompilers;
import io.github.gear4jtest.external.api.exception.PolicyViolationException;
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.DependencyInjector;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import io.github.gear4jtest.external.api.loader.SimpleDependencyInjector;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;
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
public class AssemblyLineManager implements AutoCloseable {
    public static final long DEFAULT_MAX_ARTIFACT_SIZE_BYTES = ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES;

    private final OperationChainTagRepository chainTagRepo;
    private final AssemblyLineStoreResolver storeResolver;
    private final AssemblyLineAliasService aliasService;
    private final AssemblyLinePublicationService publicationService;
    private final AssemblyLineLookupService lookupService;
    private final GeneratedAssemblyLineLoader loader;
    private final BoundedGeneratedSourceCompiler compilationRuntime;

    public static Builder builder() {
        return new Builder();
    }

    private AssemblyLineManager(Builder builder) {
        ClassLoader parent = builder.generatedClassParent != null ? builder.generatedClassParent : contextClassLoader();
        GeneratedSourceCompiler selectedCompiler = builder.compiler != null ? builder.compiler
                : GeneratedSourceCompilers.defaultCompiler(parent);
        this.compilationRuntime = new BoundedGeneratedSourceCompiler(selectedCompiler,
                BoundedGeneratedSourceCompiler.DEFAULT_MAX_ENTRIES,
                BoundedGeneratedSourceCompiler.DEFAULT_MAX_BYTECODE_BYTES,
                builder.compilationConfiguration);
        GeneratedSourceCompiler effectiveCompiler = compilationRuntime;
        DependencyInjector effectiveDependencyInjector = builder.dependencyInjector != null ? builder.dependencyInjector
                : new SimpleDependencyInjector();
        long effectiveMaxArtifactSizeBytes = AssemblyLineIdentifiers
                .requireValidArtifactSize(builder.maxArtifactSizeBytes);
        OperationChainPublicationRepository effectivePublicationRepository = requireAtomicPublicationRepository(builder);

        this.chainTagRepo = requireNonNull(builder.chainTagRepo);
        this.storeResolver = new AssemblyLineStoreResolver(builder.configRepo, builder.storeProvider);
        this.aliasService = new AssemblyLineAliasService(builder.classLoaderRegistry);
        this.loader = new GeneratedAssemblyLineLoader(storeResolver, builder.classLoaderRegistry,
                builder.translatorResolver, effectiveCompiler, effectiveDependencyInjector, parent,
                effectiveMaxArtifactSizeBytes, builder.loadingConfiguration);
        var publicationValidator = new AssemblyLinePublicationValidator(builder.translatorResolver,
                effectiveCompiler, effectiveMaxArtifactSizeBytes);
        this.publicationService = new AssemblyLinePublicationService(builder.configRepo, builder.objectRepo,
                effectivePublicationRepository, storeResolver, aliasService, publicationValidator,
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
        private GeneratedCompilationConfiguration compilationConfiguration = GeneratedCompilationConfiguration
                .defaults();
        private GeneratedLoadingConfiguration loadingConfiguration = GeneratedLoadingConfiguration.defaults();
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
         * object metadata and tags.
         *
         * <p>
         * This capability is mandatory. It may be provided explicitly here or by an
         * object repository that also implements
         * {@link OperationChainPublicationRepository}. JDBC object repositories provide
         * the contract directly and are detected automatically. An explicitly supplied
         * repository must publish into the same backing metadata state read by the
         * configured object and tag repositories.
         * </p>
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

        /**
         * Configures the deadline and bounded executor used for generated-source
         * compilation.
         */
        public Builder compilationConfiguration(GeneratedCompilationConfiguration compilationConfiguration) {
            this.compilationConfiguration = requireNonNull(compilationConfiguration,
                                                           "compilationConfiguration must not be null");
            return this;
        }

        /**
         * Configures the end-to-end deadline and bounded executor used to load a
         * generated assembly line.
         */
        public Builder loadingConfiguration(GeneratedLoadingConfiguration loadingConfiguration) {
            this.loadingConfiguration = requireNonNull(loadingConfiguration,
                                                       "loadingConfiguration must not be null");
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

    private static OperationChainPublicationRepository requireAtomicPublicationRepository(Builder builder) {
        OperationChainPublicationRepository repository = builder.publicationRepository;
        if (repository == null && builder.objectRepo instanceof OperationChainPublicationRepository detected) {
            repository = detected;
        }
        if (repository == null) {
            throw new IllegalStateException(
                    "Atomic metadata publication is required. Configure publicationRepository(...) "
                            + "or use an object repository that implements OperationChainPublicationRepository.");
        }
        if (!repository.supportsStaging()) {
            throw new IllegalStateException("Staged metadata publication is required. The configured publication "
                    + "repository must implement stage renewal, commit, abortIfUnchanged and reconciliation lookup "
                    + "operations.");
        }
        return repository;
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

    /**
     * Returns a point-in-time snapshot of compilation cache, deadline and
     * saturation counters.
     */
    public GeneratedCompilationStats compilationStats() {
        return compilationRuntime.snapshotStats();
    }

    /**
     * Returns a point-in-time snapshot of generated loading, phase-duration,
     * deadline and saturation counters.
     */
    public GeneratedLoadingStats loadingStats() {
        return loader.snapshotStats();
    }

    /**
     * Stops owned workers, cancels pending work and releases cached artifact-store
     * leases.
     *
     * <p>
     * Cancellation is best-effort for compiler implementations that ignore thread
     * interruption. Callers must quiesce manager operations before closing it.
     * </p>
     */
    @Override
    public void close() {
        try {
            loader.close();
        } finally {
            try {
                compilationRuntime.close();
            } finally {
                storeResolver.close();
            }
        }
    }
}
