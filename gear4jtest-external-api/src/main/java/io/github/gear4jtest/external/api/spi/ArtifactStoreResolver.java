package io.github.gear4jtest.external.api.spi;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;

public final class ArtifactStoreResolver {
    private final Map<String, ArtifactStorePlugin> byType;
    private final Set<String> availableTypes;

    public ArtifactStoreResolver(ClassLoader cl) {
        this(ServiceLoader.load(ArtifactStorePlugin.class, Objects.requireNonNull(cl, "cl must not be null")));
    }

    ArtifactStoreResolver(Iterable<? extends ArtifactStorePlugin> plugins) {
        Objects.requireNonNull(plugins, "plugins must not be null");
        Map<String, ArtifactStorePlugin> discovered = new TreeMap<>();
        for (ArtifactStorePlugin plugin : plugins) {
            ArtifactStorePlugin requiredPlugin = Objects.requireNonNull(plugin,
                                                                        "artifact-store plugin must not be null");
            String type = StoreType.of(requiredPlugin.type()).name();
            ArtifactStorePlugin previous = discovered.putIfAbsent(type, requiredPlugin);
            if (previous != null) {
                TreeSet<String> candidates = new TreeSet<>();
                candidates.add(previous.getClass().getName());
                candidates.add(requiredPlugin.getClass().getName());
                throw new IllegalStateException("Ambiguous ArtifactStorePlugin for " + type + ": " + candidates);
            }
        }
        byType = Map.copyOf(discovered);
        availableTypes = Collections.unmodifiableSet(new TreeSet<>(discovered.keySet()));
    }

    public ArtifactStore resolve(String type, Map<String, String> props, ArtifactStorePlugin.Context ctx) {
        var p = plugin(type);
        p.validateProperties(props);
        try {
            return p.build(props, ctx);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build store " + type, e);
        }
    }

    public Set<String> availableTypes() {
        return availableTypes;
    }

    public ArtifactStorePropertySchema propertySchema(String type) {
        return plugin(type).propertySchema();
    }

    private ArtifactStorePlugin plugin(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Store type must not be blank");
        }
        ArtifactStorePlugin plugin = byType.get(StoreType.of(type).name());
        if (plugin == null) {
            throw new IllegalArgumentException("Unknown store type: " + type);
        }
        return plugin;
    }
}
