package io.github.gear4jtest.external.api.model;

import java.time.Instant;
import java.util.Objects;

import io.github.gear4jtest.external.api.ExecutionMode;

public record OperationChainObject(Long id,
                                   String alId,
                                   String version,
                                   ExecutionMode mode,
                                   String contentHash,
                                   long sizeBytes,
                                   String mimeType,
                                   Instant createdAt,
                                   String createdBy,
                                   Instant publishedAt) {
    public OperationChainObject {
        alId = OperationChainModelValidation.requireText(alId, "alId", 255);
        version = OperationChainModelValidation.requireText(version, "version", 100);
        mode = Objects.requireNonNull(mode, "mode must not be null");
        OperationChainContentIdentity contentIdentity = new OperationChainContentIdentity(contentHash, sizeBytes,
                mimeType);
        contentHash = contentIdentity.contentHash();
        sizeBytes = contentIdentity.sizeBytes();
        mimeType = contentIdentity.mimeType();
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        createdBy = OperationChainModelValidation.optionalText(createdBy, "createdBy", 200);
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }

    /**
     * Returns the complete identity used to compare publication content.
     *
     * @return hash, byte size and media type of this publication
     */
    public OperationChainContentIdentity contentIdentity() {
        return OperationChainContentIdentity.from(this);
    }
}
