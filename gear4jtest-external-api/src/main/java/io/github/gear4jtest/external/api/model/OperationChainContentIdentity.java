package io.github.gear4jtest.external.api.model;

import java.util.Objects;

/**
 * Content identity shared by publication, staging, commit and promotion.
 *
 * <p>
 * A SHA-256 hash alone is not sufficient to compare persisted publication
 * metadata. Two publications are identical only when their hash, byte size and
 * media type all match.
 * </p>
 *
 * @param contentHash SHA-256 hash of the artifact content
 * @param sizeBytes   artifact size in bytes
 * @param mimeType    artifact media type
 */
public record OperationChainContentIdentity(String contentHash, long sizeBytes, String mimeType) {
    public OperationChainContentIdentity {
        contentHash = OperationChainModelValidation.requireSha256(contentHash);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
        mimeType = OperationChainModelValidation.requireText(mimeType, "mimeType", 100);
    }

    /**
     * Extracts the content identity of a publication object.
     *
     * @param object publication metadata
     * @return the publication content identity
     */
    public static OperationChainContentIdentity from(OperationChainObject object) {
        OperationChainObject requiredObject = Objects.requireNonNull(object, "object must not be null");
        return new OperationChainContentIdentity(requiredObject.contentHash(), requiredObject.sizeBytes(),
                requiredObject.mimeType());
    }
}
