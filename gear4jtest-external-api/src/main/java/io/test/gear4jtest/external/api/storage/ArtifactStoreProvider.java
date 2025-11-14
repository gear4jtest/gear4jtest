package io.test.gear4jtest.external.api.storage;

import io.test.gear4jtest.external.api.artifact.ArtifactStore;
import io.test.gear4jtest.external.api.model.OperationChainConfig;

public interface ArtifactStoreProvider {
    ArtifactStore forConfig(OperationChainConfig cfg);
}
