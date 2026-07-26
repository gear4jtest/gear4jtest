package io.github.gear4jtest.external.api.spi;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;

/**
 * Resolves the artifact store configured for an external assembly line.
 *
 * <p>
 * Implementations may cache stores, but must return a store compatible with the
 * supplied configuration.
 * </p>
 */
public interface ArtifactStoreProvider {
    ArtifactStore forConfig(OperationChainConfig config);
}
