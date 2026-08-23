package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
import java.util.Objects;

import io.github.gear4jtest.external.api.model.OperationChainObject;

/**
 * Stable continuation key for operation-chain objects ordered by publication
 * time and identifier in descending order.
 */
public record OperationChainObjectCursor(Instant publishedAt, long id) {
    public OperationChainObjectCursor {
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        if (id <= 0L) {
            throw new IllegalArgumentException("id must be > 0");
        }
    }

    public static OperationChainObjectCursor after(OperationChainObject object) {
        Objects.requireNonNull(object, "object must not be null");
        if (object.id() == null) {
            throw new IllegalArgumentException("object must have a persistent identifier");
        }
        return new OperationChainObjectCursor(object.publishedAt(), object.id());
    }
}
