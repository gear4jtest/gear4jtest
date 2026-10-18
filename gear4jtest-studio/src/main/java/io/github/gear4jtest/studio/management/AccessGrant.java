package io.github.gear4jtest.studio.management;

import java.util.Objects;
import java.util.Set;

public record AccessGrant(SubjectType subjectType, String subject, Set<Permission> permissions) {
    public enum SubjectType {
        USER, GROUP, AUTHENTICATED
    }

    public AccessGrant {
        Objects.requireNonNull(subjectType);
        StudioIdentity.identifier(subject);
        permissions = Set.copyOf(permissions);
        if (permissions.isEmpty() || subjectType == SubjectType.AUTHENTICATED && !subject.equals("*"))
            throw new IllegalArgumentException("Invalid grant");
    }

    public boolean matches(StudioIdentity actor) {
        return switch (subjectType) {
            case USER -> subject.equals(actor.principalId());
            case GROUP -> actor.groups().contains(subject);
            case AUTHENTICATED -> true;
        };
    }
}
