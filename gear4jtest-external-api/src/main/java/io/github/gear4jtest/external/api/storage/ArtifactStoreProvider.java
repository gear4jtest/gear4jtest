package io.github.gear4jtest.external.api.storage;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;

public interface ArtifactStoreProvider {
    ArtifactStore forConfig(OperationChainConfig cfg);
}
