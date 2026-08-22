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
        return new InMemoryArtifactStore(
                parseLong(props, "maxArtifactSizeBytes", ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES),
                parseLong(props, "maxTotalBytes", InMemoryArtifactStore.DEFAULT_MAX_TOTAL_BYTES),
                parseInt(props, "maxEntries", InMemoryArtifactStore.DEFAULT_MAX_ENTRIES));
    }

    private static long parseLong(Map<String, String> properties, String name, long defaultValue) {
        String value = properties == null ? null : properties.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid MEMORY artifact store property '" + name + "': " + value,
                    exception);
        }
    }

    private static int parseInt(Map<String, String> properties, String name, int defaultValue) {
        String value = properties == null ? null : properties.get(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid MEMORY artifact store property '" + name + "': " + value,
                    exception);
        }
    }
}
