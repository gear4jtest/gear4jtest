package io.github.gear4jtest.external.api.spi;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;

public final class ArtifactStoreResolver {
    private final Map<String, ArtifactStorePlugin> byType = new HashMap<>();

    public ArtifactStoreResolver(ClassLoader cl) {
        ServiceLoader.load(ArtifactStorePlugin.class, cl)
                .forEach(p -> byType.put(p.type().toUpperCase(Locale.ROOT), p));
    }

    public ArtifactStore resolve(String type, Map<String, String> props, ArtifactStorePlugin.Context ctx) {
        var p = byType.get(type.toUpperCase(Locale.ROOT));
        if (p == null)
            throw new IllegalArgumentException("Unknown store type: " + type);
        try {
            return p.build(props, ctx);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build store " + type, e);
        }
    }

    public Set<String> availableTypes() {
        return Set.copyOf(byType.keySet());
    }
}
