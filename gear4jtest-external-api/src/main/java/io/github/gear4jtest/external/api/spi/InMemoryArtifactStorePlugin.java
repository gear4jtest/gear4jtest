package io.github.gear4jtest.external.api.spi;

import java.util.Map;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.InMemoryArtifactStore;

public final class InMemoryArtifactStorePlugin implements ArtifactStorePlugin {
    @Override
    public String type() {
        return "MEMORY";
    }

    @Override
    public ArtifactStore build(Map<String, String> props, Context ctx) {
        return new InMemoryArtifactStore();
    }
}
