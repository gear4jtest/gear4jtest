package io.github.gear4jtest.studio.model;

import java.util.Set;

public record RuntimeContext(String id, String applicationBuild, String catalogFingerprint, Set<String> capabilities) {
    public RuntimeContext {
        capabilities = Set.copyOf(capabilities);
    }
}
