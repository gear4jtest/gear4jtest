package io.github.gear4jtest.external.api.spi;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;

/**
 * Resolves the artifact store configured for an external assembly line.
 *
 * <p>
 * Implementations may cache stores, but must return a store compatible with the
 * supplied configuration. Consumers must balance acquired stores with
 * {@link #release(ArtifactStore)}; providers that return application-owned
 * stores may keep the default no-op release behavior.
 * </p>
 */
public interface ArtifactStoreProvider {
    ArtifactStore forConfig(OperationChainConfig config);

    /**
     * Releases one store lease previously returned by {@link #forConfig}.
     *
     * <p>
     * The default is deliberately a no-op so application-owned or shared providers
     * remain compatible. Providers that create or lease stores should override this
     * method and release owned resources once their final lease is returned.
     * </p>
     */
    default void release(ArtifactStore store) {
        // The provider does not own stores unless it explicitly opts in.
    }
}
