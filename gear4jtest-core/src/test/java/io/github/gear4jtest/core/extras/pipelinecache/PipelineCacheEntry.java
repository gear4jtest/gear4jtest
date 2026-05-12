package io.github.gear4jtest.core.extras.pipelinecache;

import java.time.Instant;
import java.util.Objects;

public final class PipelineCacheEntry<OUT> {
    private final PipelineCacheKey key;
    private final OUT output;
    private final Instant expiresAt;
    private final Instant createdAt;

    public PipelineCacheEntry(PipelineCacheKey key, OUT output, Instant expiresAt, Instant createdAt) {
        this.key = Objects.requireNonNull(key, "key");
        this.output = output;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public PipelineCacheKey key() {
        return key;
    }

    public OUT output() {
        return output;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isValidAt(Instant instant) {
        return expiresAt.isAfter(instant);
    }
}
