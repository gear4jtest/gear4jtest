package io.github.gear4jtest.studio.management;

import java.util.EnumSet;
import java.util.Set;

import io.github.gear4jtest.studio.service.StudioProblem;

import static io.github.gear4jtest.studio.service.StudioProblem.Code.*;

final class ResourceAccess {
    private ResourceAccess() {
    }

    static ManagedResource resource(StudioStore.Session tx, java.util.UUID id) {
        var resource = tx.resource(id);
        if (resource == null)
            throw new StudioProblem(NOT_FOUND);
        return resource;
    }

    static StudioChain chain(StudioStore.Session tx, StudioDraft draft) {
        return (StudioChain) resource(tx, draft.chainId());
    }

    static StudioDomain domain(StudioStore.Session tx, ManagedResource resource) {
        if (resource instanceof StudioDomain d)
            return d;
        if (resource instanceof StudioDraft d)
            return domain(tx, chain(tx, d));
        return (StudioDomain) resource(tx, ((StudioChain) resource).domainId());
    }

    static Set<Permission> permissions(StudioStore.Session tx, StudioIdentity actor, ManagedResource resource) {
        var domain = domain(tx, resource);
        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        if (actor.administrator() || actor.subject().equals(domain.owner()) || actor.subject().equals(resource.owner()))
            result.addAll(EnumSet.allOf(Permission.class));
        else {
            if (resource.inheritAccess()) {
                ManagedResource parent = resource instanceof StudioDraft d ? chain(tx, d) : domain;
                result.addAll(permissions(tx, actor, parent));
            }
            tx.grants(resource.id()).stream().filter(g -> g.matches(actor))
                    .forEach(g -> result.addAll(g.permissions()));
        }
        if (result.contains(Permission.READ))
            result.add(Permission.DISCOVER);
        boolean frozen = domain.archived()
                || resource instanceof StudioChain c && c.archived()
                || resource instanceof StudioDraft d && (d.abandoned() || chain(tx, d).archived());
        if (frozen)
            result.removeAll(EnumSet.of(Permission.EDIT, Permission.CREATE_CHAIN, Permission.CREATE_DRAFT,
                                        Permission.TEST, Permission.VALIDATE, Permission.ARCHIVE, Permission.PUBLISH,
                                        Permission.PROMOTE));
        return Set.copyOf(result);
    }

    static Set<Permission> require(StudioStore.Session tx,
                                   StudioIdentity actor,
                                   ManagedResource resource,
                                   Permission action) {
        var permissions = permissions(tx, actor, resource);
        if (!permissions.contains(Permission.DISCOVER))
            throw new StudioProblem(NOT_FOUND);
        if (!permissions.contains(action))
            throw new StudioProblem(FORBIDDEN);
        return permissions;
    }

    static Set<Permission> requireRead(StudioStore.Session tx,
                                       StudioIdentity actor,
                                       ManagedResource resource,
                                       Permission action) {
        require(tx, actor, resource, Permission.READ);
        return require(tx, actor, resource, action);
    }
}
