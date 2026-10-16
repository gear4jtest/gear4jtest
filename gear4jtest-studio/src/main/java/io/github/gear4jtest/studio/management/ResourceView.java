package io.github.gear4jtest.studio.management;

import java.util.Set;

public record ResourceView<T extends ManagedResource>(T resource, Set<Permission> permissions) {
    public ResourceView {
        permissions = Set.copyOf(permissions);
    }
}
