package io.github.gear4jtest.external.api.spi;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Declares the property names understood by an artifact-store plugin.
 *
 * <p>
 * Third-party plugins remain open by default. Built-in plugins use closed
 * schemas so configuration typos fail before a store is used.
 * </p>
 */
public record ArtifactStorePropertySchema(Set<String> supportedProperties, boolean allowsUnknownProperties) {
    private static final ArtifactStorePropertySchema OPEN = new ArtifactStorePropertySchema(Set.of(), true);

    public ArtifactStorePropertySchema {
        supportedProperties = Set.copyOf(Objects.requireNonNull(supportedProperties,
                                                                "supportedProperties must not be null"));
        if (supportedProperties.stream().anyMatch(name -> name == null || name.isBlank())) {
            throw new IllegalArgumentException("supportedProperties must not contain blank names");
        }
    }

    public static ArtifactStorePropertySchema open() {
        return OPEN;
    }

    public static ArtifactStorePropertySchema closed(String... supportedProperties) {
        return new ArtifactStorePropertySchema(Set.of(supportedProperties), false);
    }

    public void validate(String storeType, Map<String, String> properties) {
        if (allowsUnknownProperties || properties == null || properties.isEmpty()) {
            return;
        }
        Set<String> unknown = new TreeSet<>(properties.keySet());
        unknown.removeAll(supportedProperties);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unsupported " + storeType + " artifact store properties: "
                    + unknown + ". Supported properties: " + new TreeSet<>(supportedProperties));
        }
    }
}
