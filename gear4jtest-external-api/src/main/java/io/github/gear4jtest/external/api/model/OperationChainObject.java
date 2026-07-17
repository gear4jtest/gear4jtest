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
        alId = OperationChainModelValidation.requireText(alId, "alId", 200);
        version = OperationChainModelValidation.requireText(version, "version", 100);
        mode = Objects.requireNonNull(mode, "mode must not be null");
        contentHash = OperationChainModelValidation.requireSha256(contentHash);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
        mimeType = OperationChainModelValidation.requireText(mimeType, "mimeType", 100);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        createdBy = OperationChainModelValidation.optionalText(createdBy, "createdBy", 200);
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
    }
}
