package io.github.gear4jtest.external.api.consistency;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.artifact.Artifact;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.spi.ArtifactStoreProvider;

/**
 * Checks metadata-to-artifact references without assuming a database-only
 * store.
 */
public final class ArtifactConsistencyChecker {
    private static final int DEFAULT_PAGE_SIZE = 500;

    private final OperationChainConfigRepository configRepository;
    private final OperationChainObjectRepository objectRepository;
    private final ArtifactStoreProvider storeProvider;
    private final int pageSize;

    public ArtifactConsistencyChecker(OperationChainConfigRepository configRepository,
                                      OperationChainObjectRepository objectRepository,
                                      ArtifactStoreProvider storeProvider) {
        this(configRepository, objectRepository, storeProvider, DEFAULT_PAGE_SIZE);
    }

    public ArtifactConsistencyChecker(OperationChainConfigRepository configRepository,
                                      OperationChainObjectRepository objectRepository,
                                      ArtifactStoreProvider storeProvider,
                                      int pageSize) {
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository must not be null");
        this.objectRepository = Objects.requireNonNull(objectRepository, "objectRepository must not be null");
        this.storeProvider = Objects.requireNonNull(storeProvider, "storeProvider must not be null");
        if (pageSize <= 0 || pageSize > PageRequest.MAX_LIMIT) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + PageRequest.MAX_LIMIT);
        }
        this.pageSize = pageSize;
    }

    public Report check(String assemblyLineId) throws IOException {
        if (assemblyLineId == null || assemblyLineId.isBlank()) {
            throw new IllegalArgumentException("assemblyLineId is required");
        }
        OperationChainConfig config = configRepository.findByAssemblyLineId(assemblyLineId)
                .orElseThrow(() -> new OperationChainNotFoundException(
                        "Operation-chain configuration not found for " + assemblyLineId));
        ArtifactStore store = Objects.requireNonNull(storeProvider.forConfig(config),
                                                     "storeProvider returned null");
        try {
            List<Issue> issues = new ArrayList<>();
            Map<String, ArtifactMetadata> artifactsByHash = new HashMap<>();
            int objectsChecked = 0;
            int offset = 0;
            while (true) {
                List<OperationChainObject> page = objectRepository.findAll(assemblyLineId,
                                                                           new PageRequest(offset, pageSize));
                for (OperationChainObject object : page) {
                    objectsChecked++;
                    ArtifactMetadata artifact = artifactsByHash.get(object.contentHash());
                    if (artifact == null) {
                        artifact = loadMetadata(store, object.contentHash());
                        artifactsByHash.put(object.contentHash(), artifact);
                    }
                    if (!artifact.present()) {
                        issues.add(Issue.missing(object));
                    } else if (artifact.sizeBytes() != object.sizeBytes()) {
                        issues.add(Issue.sizeMismatch(object, artifact.sizeBytes()));
                    }
                }
                if (page.size() < pageSize) {
                    break;
                }
                try {
                    offset = Math.addExact(offset, pageSize);
                } catch (ArithmeticException exception) {
                    throw new IllegalStateException("Artifact consistency pagination offset overflow", exception);
                }
            }
            return new Report(assemblyLineId, objectsChecked, artifactsByHash.size(), issues);
        } finally {
            storeProvider.release(store);
        }
    }

    private static ArtifactMetadata loadMetadata(ArtifactStore store, String hash) throws IOException {
        return store.get(hash)
                .map(ArtifactConsistencyChecker::metadata)
                .orElseGet(ArtifactMetadata::missing);
    }

    private static ArtifactMetadata metadata(Artifact artifact) {
        return new ArtifactMetadata(true, artifact.size());
    }

    public record Report(String assemblyLineId,
                         int objectsChecked,
                         int uniqueArtifactsChecked,
                         List<Issue> issues) {
        public Report {
            Objects.requireNonNull(assemblyLineId, "assemblyLineId must not be null");
            if (objectsChecked < 0 || uniqueArtifactsChecked < 0) {
                throw new IllegalArgumentException("consistency counters must be >= 0");
            }
            issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        }

        public boolean consistent() {
            return issues.isEmpty();
        }
    }

    public record Issue(Type type,
                        String assemblyLineId,
                        String version,
                        ExecutionMode mode,
                        String contentHash,
                        long expectedSizeBytes,
                        Long actualSizeBytes) {
        public Issue {
            Objects.requireNonNull(type, "type must not be null");
            Objects.requireNonNull(assemblyLineId, "assemblyLineId must not be null");
            Objects.requireNonNull(version, "version must not be null");
            Objects.requireNonNull(mode, "mode must not be null");
            Objects.requireNonNull(contentHash, "contentHash must not be null");
        }

        private static Issue missing(OperationChainObject object) {
            return new Issue(Type.MISSING_ARTIFACT, object.alId(), object.version(), object.mode(),
                    object.contentHash(), object.sizeBytes(), null);
        }

        private static Issue sizeMismatch(OperationChainObject object, long actualSize) {
            return new Issue(Type.SIZE_MISMATCH, object.alId(), object.version(), object.mode(), object.contentHash(),
                    object.sizeBytes(), actualSize);
        }
    }

    public enum Type {
        MISSING_ARTIFACT,
        SIZE_MISMATCH
    }

    private record ArtifactMetadata(boolean present, long sizeBytes) {
        private static ArtifactMetadata missing() {
            return new ArtifactMetadata(false, -1L);
        }
    }
}
