package io.github.gear4jtest.studio.standalone;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import io.github.gear4jtest.studio.management.StudioIdentity;

/**
 * Immutable host allowlist. Visibility does not grant any remote resource
 * permission.
 */
public final class TargetDirectory {
    private final Map<String, StudioApplication> applications;
    private final Map<String, StudioTarget> targets;
    private final BiPredicate<StudioIdentity, StudioTarget> access;

    public TargetDirectory(List<StudioApplication> applications,
                           List<StudioTarget> targets,
                           BiPredicate<StudioIdentity, StudioTarget> access) {
        if (applications.isEmpty() || applications.size() > 32 || targets.isEmpty() || targets.size() > 32)
            throw new IllegalArgumentException("Directory capacity is 1..32 applications and targets");
        this.applications = new LinkedHashMap<>();
        this.targets = new LinkedHashMap<>();
        this.access = Objects.requireNonNull(access);
        for (var app : applications)
            if (this.applications.putIfAbsent(app.id(), app) != null)
                throw new IllegalArgumentException("Duplicate application");
        var contexts = new java.util.HashSet<String>();
        for (var target : targets)
            if (!this.applications.containsKey(target.applicationId())
                    || this.targets.putIfAbsent(target.id(), target) != null
                    || !contexts.add(target.runtimeContextId()))
                throw new IllegalArgumentException("Ambiguous target binding");
    }

    public List<TargetView> visible(StudioIdentity actor) {
        Objects.requireNonNull(actor);
        return targets.values().stream().filter(t -> access.test(actor, t))
                .map(t -> new TargetView(t.id(), t.applicationId(), applications.get(t.applicationId()).name(),
                        t.name(), t.runtimeContextId()))
                .toList();
    }

    public StudioTarget require(StudioIdentity actor, String id) {
        Objects.requireNonNull(actor);
        var target = targets.get(id);
        if (target == null || !access.test(actor, target))
            throw new RemoteProblem("TARGET_UNAVAILABLE", 404);
        return target;
    }

    static void identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,63}"))
            throw new IllegalArgumentException("Invalid registry identifier");
    }

    static void label(String value) {
        if (value == null || value.isBlank() || value.length() > 160 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid registry label");
    }

    /**
     * Safe inventory view: endpoint URLs and credentials are never sent to
     * browsers.
     */
    public record TargetView(String id,
                             String applicationId,
                             String applicationName,
                             String name,
                             String runtimeContextId) {}
}
