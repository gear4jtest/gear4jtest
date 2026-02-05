package io.github.gear4jtest.core.engine.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.engine.spi.RuntimeExtension;

public class ExtensionRegistry {
    
    // Map: Feature Name -> Instance de l'Extension
    private final Map<String, RuntimeExtension> extensions = new HashMap<>();

    public void register(String feature, RuntimeExtension extension) {
        extensions.put(feature, extension);
    }

    public List<RuntimeExtension> find(List<String> requestedFeatures) {
        List<RuntimeExtension> found = new ArrayList<>();
        if (requestedFeatures == null) return found;

        for (String feature : requestedFeatures) {
            if (extensions.containsKey(feature)) {
                found.add(extensions.get(feature));
            } else {
                // On peut décider de logger un warning ou d'ignorer
            }
        }
        return found;
    }
}
