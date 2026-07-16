package io.github.gear4jtest.external.api.repository;

import java.util.List;

import io.github.gear4jtest.external.api.model.OperationChainObject;

/**
 * Persists one operation-chain object and its tags as one atomic metadata
 * publication.
 *
 * <p>
 * Implementations must provide all-or-nothing semantics: after any failure,
 * neither a new object nor any of its requested tags may be visible.
 * Implementations must also be idempotent for the natural key
 * {@code (assemblyLineId, version, mode)} when the existing object references
 * the same content. A conflicting content hash or metadata must be rejected
 * without changing the existing object or tags. A successful publication must
 * be visible through the object and tag repositories paired with this
 * capability.
 * </p>
 */
public interface OperationChainPublicationRepository {
    void publish(OperationChainObject object, List<String> tags);
}
