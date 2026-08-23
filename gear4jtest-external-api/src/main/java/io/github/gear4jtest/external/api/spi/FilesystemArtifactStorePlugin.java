package io.github.gear4jtest.external.api.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.FilesystemArtifactStore;

public final class FilesystemArtifactStorePlugin implements ArtifactStorePlugin {
    private static final ArtifactStorePropertySchema PROPERTY_SCHEMA = ArtifactStorePropertySchema.closed(
                                                                                                          "root",
                                                                                                          "path",
                                                                                                          "maxArtifactSizeBytes");

    @Override
    public String type() {
        return "FILESYSTEM";
    }

    @Override
    public ArtifactStorePropertySchema propertySchema() {
        return PROPERTY_SCHEMA;
    }

    @Override
    public ArtifactStore build(Map<String, String> props, Context ctx) throws IOException {
        validateProperties(props);
        String root = props == null ? null : props.get("root");
        if (root == null || root.isBlank()) {
            root = props == null ? null : props.get("path");
        }
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("FILESYSTEM artifact store requires property 'root'");
        }
        return new FilesystemArtifactStore(Path.of(root), requireMaxArtifactSize(props));
    }

    private static long requireMaxArtifactSize(Map<String, String> props) {
        String value = props == null ? null : props.get("maxArtifactSizeBytes");
        if (value == null || value.isBlank()) {
            return ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid FILESYSTEM artifact store maxArtifactSizeBytes: " + value
                    + ". Expected a non-negative byte count.", exception);
        }
    }
}
