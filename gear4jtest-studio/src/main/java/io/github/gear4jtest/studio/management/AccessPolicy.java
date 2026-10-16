package io.github.gear4jtest.studio.management;

import java.util.HashSet;
import java.util.List;

public record AccessPolicy(boolean inheritAccess, List<AccessGrant> grants) {
    public AccessPolicy {
        grants = List.copyOf(grants);
        if (grants.size() > 64)
            throw new IllegalArgumentException("Too many grants");
        var subjects = new HashSet<String>();
        for (var grant : grants)
            if (!subjects.add(grant.subjectType() + ":" + grant.subject()))
                throw new IllegalArgumentException("Duplicate grant subject");
    }
}
