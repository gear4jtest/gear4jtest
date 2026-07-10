package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainTagRepository;

import static java.util.Objects.requireNonNull;

final class AssemblyLinePublicationService {
    private final OperationChainConfigRepository configRepository;
    private final OperationChainObjectRepository objectRepository;
    private final OperationChainTagRepository tagRepository;
    private final OperationChainPublicationRepository publicationRepository;
    private final AssemblyLineStoreResolver storeResolver;
    private final AssemblyLineAliasService aliasService;
    private final AssemblyLinePublicationValidator publicationValidator;
    private final long maxArtifactSizeBytes;

    AssemblyLinePublicationService(OperationChainConfigRepository configRepository,
                                   OperationChainObjectRepository objectRepository,
                                   OperationChainTagRepository tagRepository,
                                   OperationChainPublicationRepository publicationRepository,
                                   AssemblyLineStoreResolver storeResolver,
                                   AssemblyLineAliasService aliasService,
                                   AssemblyLinePublicationValidator publicationValidator,
                                   long maxArtifactSizeBytes) {
        this.configRepository = requireNonNull(configRepository);
        this.objectRepository = requireNonNull(objectRepository);
        this.tagRepository = requireNonNull(tagRepository);
        this.publicationRepository = publicationRepository;
        this.storeResolver = requireNonNull(storeResolver);
        this.aliasService = requireNonNull(aliasService);
        this.publicationValidator = requireNonNull(publicationValidator);
        this.maxArtifactSizeBytes = AssemblyLineIdentifiers.requireValidArtifactSize(maxArtifactSizeBytes);
    }

    String registerAssemblyLine(String alId,
                                String version,
                                ExecutionMode mode,
                                byte[] content,
                                String mediaType,
                                List<String> tags,
                                String createdBy)
            throws IOException, AssemblyLineManager.PolicyViolationException {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required for persisted TEST/RUN publication");
        }
        requireNonNull(alId);
        requireNonNull(mode);
        requireNonNull(content);

        if (mode == ExecutionMode.RUN) {
            var config = configRepository.findByAssemblyLineId(alId)
                    .orElseThrow(() -> new NoSuchElementException("Config not found for alId=" + alId));
            if (!Boolean.TRUE.equals(config.allowRunPublicationWithoutTest())) {
                throw new AssemblyLineManager.PolicyViolationException(
                        "Direct RUN publication is disabled for alId=" + alId);
            }
        }

        AssemblyLineIdentifiers.requireAllowedArtifactSize(content.length, maxArtifactSizeBytes,
                                                           "Assembly line artifact");
        ArtifactStore store = storeResolver.resolve(alId);
        String hash = store.put(content);

        OperationChainObject obj = new OperationChainObject(null, alId, version, mode, hash, content.length,
                AssemblyLineIdentifiers.normalizeMediaType(mediaType), Instant.now(), createdBy, Instant.now());
        publicationValidator.validatePublicationCandidate(alId, obj);
        publishMetadata(obj, tags);
        if (mode == ExecutionMode.RUN) {
            aliasService.invalidateLatestRun(alId);
        }
        return hash;
    }

    void promoteTestToRun(String alId, String version, String promotedBy)
            throws AssemblyLineManager.PolicyViolationException {
        var testObj = objectRepository.find(alId, version, ExecutionMode.TEST).orElseThrow(
                                                                                           () -> new NoSuchElementException(
                                                                                                   "TEST object not found for %s:%s"
                                                                                                           .formatted(alId,
                                                                                                                      version)));
        if (objectRepository.exists(alId, version, ExecutionMode.RUN)) {
            var runObj = objectRepository.find(alId, version, ExecutionMode.RUN).orElseThrow();
            if (!Objects.equals(runObj.contentHash(), testObj.contentHash())) {
                throw new AssemblyLineManager.PolicyViolationException(
                        "RUN object already exists with different content_hash");
            }
            return;
        }
        var runObj = new OperationChainObject(null, alId, version, ExecutionMode.RUN, testObj.contentHash(),
                testObj.sizeBytes(), testObj.mimeType(), Instant.now(), promotedBy, Instant.now());
        publicationValidator.validateRunCandidate(alId, runObj);
        publishMetadata(runObj, List.of());
        aliasService.invalidateLatestRun(alId);
    }

    private void publishMetadata(OperationChainObject object, List<String> tags)
            throws AssemblyLineManager.PolicyViolationException {
        List<String> publicationTags = tags == null ? List.of() : tags.stream().distinct().toList();
        try {
            if (publicationRepository != null) {
                publicationRepository.publish(object, publicationTags);
                return;
            }
            objectRepository.insert(object);
            for (String tag : publicationTags) {
                tagRepository.addTag(object.alId(), tag);
            }
        } catch (OperationChainPublicationConflictException exception) {
            throw new AssemblyLineManager.PolicyViolationException(exception.getMessage(), exception);
        }
    }
}
