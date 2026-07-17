package io.github.gear4jtest.external.api.repository;

import java.time.Instant;
import java.util.List;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.storage.ArtifactStoreConfigurationFingerprint;

/**
 * Persists operation-chain metadata using an atomic publication contract.
 *
 * <p>
 * Implementations used by {@code AssemblyLineManager} must support the staged
 * lifecycle. A stage is durable but invisible through the normal object/tag
 * repositories. {@link #commit(String)} atomically publishes the object and its
 * tags, while {@link #abort(String)} discards an uncommitted stage.
 * </p>
 *
 * <p>
 * Implementations must be idempotent for the natural key
 * {@code (assemblyLineId, version, mode)} when the existing object references
 * the same content. A conflicting content hash or metadata must be rejected
 * without changing committed or staged state.
 * </p>
 */
public interface OperationChainPublicationRepository {
    /**
     * Backward-compatible one-shot publication entry point.
     *
     * <p>
     * Staging-capable implementations should implement this operation as
     * {@code stage + commit}.
     * </p>
     */
    void publish(OperationChainObject object, List<String> tags);

    /**
     * Whether this repository implements the durable staged lifecycle.
     */
    default boolean supportsStaging() {
        return false;
    }

    /**
     * Creates or reuses an idempotent stage for the supplied publication.
     */
    default OperationChainPublicationStage stage(OperationChainObject object, List<String> tags) {
        return stage(object, tags, ArtifactStoreConfigurationFingerprint.UNSPECIFIED);
    }

    /**
     * Creates or renews an idempotent stage tied to the artifact-store
     * configuration that will receive the bytes.
     *
     * <p>
     * An idempotent retry must refresh the stage age and increment its revision. A
     * retry that targets different content or a different store fingerprint must be
     * rejected without modifying the existing stage.
     * </p>
     */
    default OperationChainPublicationStage stage(OperationChainObject object,
                                                 List<String> tags,
                                                 String storeFingerprint) {
        throw new UnsupportedOperationException("Staged publication is not supported by this repository");
    }

    /**
     * Atomically makes the staged object and tags visible, then removes the stage.
     */
    default void commit(String stageId) {
        throw new UnsupportedOperationException("Staged publication is not supported by this repository");
    }

    /**
     * Removes an uncommitted stage. Calling this method for an absent stage is
     * idempotent.
     */
    default void abort(String stageId) {
        throw new UnsupportedOperationException("Staged publication is not supported by this repository");
    }

    /**
     * Removes the stage only if it is still the exact stage observed by the caller.
     * Implementations must return {@code false} when the stage was renewed,
     * replaced, committed or already removed.
     *
     * <p>
     * This optimistic guard prevents a reconciler from aborting a publication that
     * has been retried and is actively uploading its artifact.
     * </p>
     *
     * <p>
     * Staging-capable repositories used by {@code AssemblyLineManager} must
     * implement this operation; an unconditional delete is not a safe substitute.
     * </p>
     */
    default boolean abortIfUnchanged(OperationChainPublicationStage expectedStage) {
        throw new UnsupportedOperationException("Conditional staged abort is not supported by this repository");
    }

    /**
     * Returns stages created at or before the supplied cutoff.
     */
    default List<OperationChainPublicationStage> findStagedBefore(Instant cutoff, PageRequest pageRequest) {
        throw new UnsupportedOperationException("Staged publication is not supported by this repository");
    }
}
