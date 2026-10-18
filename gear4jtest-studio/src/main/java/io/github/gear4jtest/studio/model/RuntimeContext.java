package io.github.gear4jtest.studio.model;

import java.util.Set;

/**
 * Stable global identity of a management store within a federation, not an
 * environment label or a JVM instance. Prefer e.g. orders-prod.&lt;UUID&gt;.
 * Replicas sharing this store/context belong to one logical target. Never
 * regenerate the id on restart or derive it from an application build.
 */
public record RuntimeContext(String id, String applicationBuild, String catalogFingerprint, Set<String> capabilities) {
    public RuntimeContext {
        capabilities = Set.copyOf(capabilities);
    }
}
