package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainConfigRepository;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;

import static java.util.Objects.requireNonNull;

final class AssemblyLinePublicationService {
    private static final int MAX_TAG_LENGTH = 100;

    private final OperationChainConfigRepository configRepository;
    private final OperationChainObjectRepository objectRepository;
    private final OperationChainPublicationRepository publicationRepository;
    private final AssemblyLineStoreResolver storeResolver;
    private final AssemblyLineAliasService aliasService;
    private final AssemblyLinePublicationValidator publicationValidator;
    private final long maxArtifactSizeBytes;

    AssemblyLinePublicationService(OperationChainConfigRepository configRepository,
                                   OperationChainObjectRepository objectRepository,
                                   OperationChainPublicationRepository publicationRepository,
                                   AssemblyLineStoreResolver storeResolver,
                                   AssemblyLineAliasService aliasService,
                                   AssemblyLinePublicationValidator publicationValidator,
                                   long maxArtifactSizeBytes) {
        this.configRepository = requireNonNull(configRepository);
        this.objectRepository = requireNonNull(objectRepository);
        this.publicationRepository = requireNonNull(publicationRepository);
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
        requireNonNull(content, "content must not be null");
        String normalizedMediaType = AssemblyLineIdentifiers.normalizeMediaType(mediaType);
        List<String> publicationTags = normalizeTags(tags);
        String hash = ArtifactHashes.sha256Hex(content);
        Instant now = Instant.now();
        OperationChainObject object = new OperationChainObject(null, alId, version, mode, hash, content.length,
                normalizedMediaType, now, createdBy, now);

        validateDirectRunPolicy(object);
        AssemblyLineIdentifiers.requireAllowedArtifactSize(content.length, maxArtifactSizeBytes,
                                                           "Assembly line artifact");
        publicationValidator.validatePublicationCandidate(alId, object, content);

        AssemblyLineStoreResolver.ResolvedStore resolvedStore = storeResolver.resolveForPublication(alId);
        OperationChainPublicationStage stage = stage(object, publicationTags,
                                                     resolvedStore.configurationFingerprint());
        ArtifactStore store = resolvedStore.store();
        String storedHash = store.put(content);
        if (!hash.equals(storedHash)) {
            throw new IOException("Artifact store returned hash=" + storedHash + " but expected hash=" + hash);
        }

        commit(stage);
        if (mode == ExecutionMode.RUN) {
            aliasService.invalidateLatestRun(alId);
        }
        return hash;
    }

    void promoteTestToRun(String alId, String version, String promotedBy)
            throws AssemblyLineManager.PolicyViolationException {
        var testObj = objectRepository.find(alId, version, ExecutionMode.TEST)
                .orElseThrow(() -> new NoSuchElementException(
                        "TEST object not found for %s:%s".formatted(alId, version)));
        if (objectRepository.exists(alId, version, ExecutionMode.RUN)) {
            var runObj = objectRepository.find(alId, version, ExecutionMode.RUN).orElseThrow();
            if (!Objects.equals(runObj.contentHash(), testObj.contentHash())) {
                throw new AssemblyLineManager.PolicyViolationException(
                        "RUN object already exists with different content_hash");
            }
            return;
        }
        Instant now = Instant.now();
        var runObj = new OperationChainObject(null, alId, version, ExecutionMode.RUN, testObj.contentHash(),
                testObj.sizeBytes(), testObj.mimeType(), now, promotedBy, now);
        AssemblyLineStoreResolver.ResolvedStore resolvedStore = storeResolver.resolveForPublication(alId);
        publicationValidator.validateRunCandidate(alId, runObj, resolvedStore.store());
        commit(stage(runObj, List.of(), resolvedStore.configurationFingerprint()));
        aliasService.invalidateLatestRun(alId);
    }

    private void validateDirectRunPolicy(OperationChainObject object)
            throws AssemblyLineManager.PolicyViolationException {
        if (object.mode() != ExecutionMode.RUN) {
            return;
        }
        var config = configRepository.findByAssemblyLineId(object.alId())
                .orElseThrow(() -> new NoSuchElementException("Config not found for alId=" + object.alId()));
        if (!Boolean.TRUE.equals(config.allowRunPublicationWithoutTest())) {
            throw new AssemblyLineManager.PolicyViolationException(
                    "Direct RUN publication is disabled for alId=" + object.alId());
        }
    }

    private OperationChainPublicationStage stage(OperationChainObject object,
                                                 List<String> tags,
                                                 String storeFingerprint)
            throws AssemblyLineManager.PolicyViolationException {
        try {
            return publicationRepository.stage(object, tags, storeFingerprint);
        } catch (OperationChainPublicationConflictException exception) {
            throw new AssemblyLineManager.PolicyViolationException(exception.getMessage(), exception);
        }
    }

    private void commit(OperationChainPublicationStage stage) throws AssemblyLineManager.PolicyViolationException {
        try {
            publicationRepository.commit(stage.stageId());
        } catch (OperationChainPublicationConflictException exception) {
            abortAfterConflict(stage.stageId(), exception);
            throw new AssemblyLineManager.PolicyViolationException(exception.getMessage(), exception);
        }
    }

    private void abortAfterConflict(String stageId, RuntimeException failure) {
        try {
            publicationRepository.abort(stageId);
        } catch (RuntimeException abortFailure) {
            failure.addSuppressed(abortFailure);
        }
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .map(tag -> requireValidTag(tag))
                .distinct()
                .sorted()
                .toList();
    }

    private static String requireValidTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag must not be blank");
        }
        if (tag.length() > MAX_TAG_LENGTH) {
            throw new IllegalArgumentException("tag must not exceed " + MAX_TAG_LENGTH + " characters");
        }
        return tag;
    }
}
