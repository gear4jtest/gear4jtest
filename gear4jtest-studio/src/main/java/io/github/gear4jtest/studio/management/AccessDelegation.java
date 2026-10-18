package io.github.gear4jtest.studio.management;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;

import io.github.gear4jtest.studio.service.StudioProblem;

/**
 * Delegation changes only rights the actor can delegate; ownership is not a
 * grant.
 */
final class AccessDelegation {
    private AccessDelegation() {
    }

    static boolean administrator(StudioStore.Session tx, StudioIdentity actor, ManagedResource resource) {
        return actor.administrator() || actor.principalId().equals(resource.owner())
                || actor.principalId().equals(ResourceAccess.domain(tx, resource).owner());
    }

    static Set<Permission> grantable(StudioStore.Session tx, StudioIdentity actor, ManagedResource resource) {
        if (administrator(tx, actor, resource))
            return Set.copyOf(EnumSet.allOf(Permission.class));
        var result = EnumSet.noneOf(Permission.class);
        result.addAll(ResourceAccess.permissions(tx, actor, resource));
        // Creation grants ownership of a descendant; access management permits onward
        // delegation.
        result.removeAll(EnumSet.of(Permission.MANAGE_ACCESS, Permission.CREATE_CHAIN, Permission.CREATE_DRAFT));
        return Set.copyOf(result);
    }

    static void check(StudioStore.Session tx,
                      StudioIdentity actor,
                      ManagedResource resource,
                      AccessPolicy before,
                      AccessPolicy after) {
        if (administrator(tx, actor, resource))
            return;
        if (before.inheritAccess() != after.inheritAccess())
            denied();
        var allowed = grantable(tx, actor, resource);
        var changes = new HashMap<String, Set<Permission>>();
        for (var grant : before.grants())
            changes.put(grant.subjectType() + ":" + grant.subject(), grant.permissions());
        for (var grant : after.grants()) {
            var old = changes.remove(grant.subjectType() + ":" + grant.subject());
            var difference = EnumSet.noneOf(Permission.class);
            difference.addAll(grant.permissions());
            if (old != null) {
                for (var permission : old) {
                    if (!difference.remove(permission))
                        difference.add(permission);
                }
            }
            if (!allowed.containsAll(difference))
                denied();
        }
        for (var removed : changes.values())
            if (!allowed.containsAll(removed))
                denied();
    }

    private static void denied() {
        throw new StudioProblem(StudioProblem.Code.FORBIDDEN);
    }
}
