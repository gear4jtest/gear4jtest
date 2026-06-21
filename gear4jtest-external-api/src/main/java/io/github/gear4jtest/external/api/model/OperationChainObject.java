package io.github.gear4jtest.external.api.model;

import java.time.Instant;

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
                                   Instant publishedAt) {}
