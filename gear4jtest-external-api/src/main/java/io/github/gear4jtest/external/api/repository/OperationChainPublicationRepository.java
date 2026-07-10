package io.github.gear4jtest.external.api.repository;

import java.util.List;

import io.github.gear4jtest.external.api.model.OperationChainObject;

/**
 * Persists one operation-chain object and its tags as a single metadata
 * publication.
 *
 * <p>
 * Implementations must be idempotent for the natural key
 * {@code (assemblyLineId, version, mode)} when the existing object references
 * the same content. A conflicting content hash must be rejected.
 * </p>
 */
public interface OperationChainPublicationRepository {
    void publish(OperationChainObject object, List<String> tags);
}
