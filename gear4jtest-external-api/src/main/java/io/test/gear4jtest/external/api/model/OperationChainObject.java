package io.test.gear4jtest.external.api.model;

import java.time.Instant;

import io.test.gear4jtest.external.api.ExecutionMode;

public final class OperationChainObject {
    private final Long id;
    private final String alId;
    private final String version;
    private final ExecutionMode mode;
    private final String contentHash;
    private final long sizeBytes;
    private final String mimeType;
    private final Instant createdAt;
    private final String createdBy;
    private final Instant publishedAt;

    public OperationChainObject(Long id,
                                String alId,
                                String version,
                                ExecutionMode mode,
                                String contentHash,
                                long sizeBytes,
                                String mimeType,
                                Instant createdAt,
                                String createdBy,
                                Instant publishedAt) {
        this.id = id;
        this.alId = alId;
        this.version = version;
        this.mode = mode;
        this.contentHash = contentHash;
        this.sizeBytes = sizeBytes;
        this.mimeType = mimeType;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.publishedAt = publishedAt;
    }

    public Long id() {
        return id;
    }

    public String alId() {
        return alId;
    }

    public String version() {
        return version;
    }

    public ExecutionMode mode() {
        return mode;
    }

    public String contentHash() {
        return contentHash;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public String mimeType() {
        return mimeType;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public Instant publishedAt() {
        return publishedAt;
    }
}
