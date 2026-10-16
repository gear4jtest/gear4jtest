package io.github.gear4jtest.studio.management;

import java.util.Set;

/** Policy and its optimistic version come from one consistent snapshot. */
public record AccessState(long version, AccessPolicy policy, Set<Permission> permissions) {
    public AccessState {
        permissions = Set.copyOf(permissions);
    }
}
