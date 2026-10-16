package io.github.gear4jtest.studio.management;

import java.util.Objects;
import java.util.Set;

/**
 * Authenticated identity supplied by the host, never deserialized from a
 * management request.
 */
public record StudioIdentity(String subject, Set<String> groups, boolean administrator) {
    public StudioIdentity {
        identifier(subject);
        groups = Set.copyOf(groups);
        if (groups.size() > 64)
            throw new IllegalArgumentException("Too many groups");
        groups.forEach(StudioIdentity::identifier);
    }

    static void identifier(String value) {
        Objects.requireNonNull(value);
        if (value.isBlank() || value.length() > 255 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid principal identifier");
    }
}
