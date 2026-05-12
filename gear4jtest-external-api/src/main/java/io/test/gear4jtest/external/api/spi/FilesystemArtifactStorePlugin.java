package io.test.gear4jtest.external.api.spi;

import java.nio.file.Path;
import java.util.Map;

import io.test.gear4jtest.external.api.artifact.ArtifactStore;
import io.test.gear4jtest.external.api.artifact.FilesystemArtifactStore;

public final class FilesystemArtifactStorePlugin implements ArtifactStorePlugin {
    @Override
    public String type() {
        return "FILESYSTEM";
    }

    @Override
    public ArtifactStore build(Map<String, String> props, Context ctx) {
        String root = props == null ? null : props.get("root");
        if (root == null || root.isBlank()) {
            root = props == null ? null : props.get("path");
        }
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("FILESYSTEM artifact store requires property 'root'");
        }
        return new FilesystemArtifactStore(Path.of(root));
    }
}
