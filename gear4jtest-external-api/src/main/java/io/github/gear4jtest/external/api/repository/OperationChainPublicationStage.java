package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.storage.ArtifactStoreConfigurationFingerprint;

/**
 * Durable metadata staged before an artifact publication is committed.
 *
 * <p>
 * A stage makes the publication recoverable across failures between artifact
 * storage and final metadata visibility. Staged entries are not visible through
 * {@link OperationChainObjectRepository} until committed. The store fingerprint
 * identifies the exact artifact-store configuration selected before the upload,
 * while the revision changes whenever an idempotent retry renews the stage.
 * </p>
 */
public record OperationChainPublicationStage(String stageId,
                                             OperationChainObject object,
                                             List<String> tags,
                                             String storeFingerprint,
                                             Instant stagedAt,
                                             long revision) {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");
    public OperationChainPublicationStage {
        if (stageId == null || stageId.isBlank()) {
            throw new IllegalArgumentException("stageId must not be blank");
        }
        object = Objects.requireNonNull(object, "object must not be null");
        tags = OperationChainPublicationTags.normalize(tags);
        if (storeFingerprint == null || !SHA_256_HEX.matcher(storeFingerprint).matches()) {
            throw new IllegalArgumentException("storeFingerprint must be a lowercase SHA-256 value");
        }
        stagedAt = Objects.requireNonNull(stagedAt, "stagedAt must not be null");
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be > 0");
        }
    }

    public OperationChainPublicationStage(String stageId,
                                          OperationChainObject object,
                                          List<String> tags,
                                          Instant stagedAt) {
        this(stageId, object, tags, ArtifactStoreConfigurationFingerprint.UNSPECIFIED, stagedAt, 1L);
    }

    public OperationChainPublicationStage(String stageId,
                                          OperationChainObject object,
                                          List<String> tags,
                                          String storeFingerprint,
                                          Instant stagedAt) {
        this(stageId, object, tags, storeFingerprint, stagedAt, 1L);
    }

}
