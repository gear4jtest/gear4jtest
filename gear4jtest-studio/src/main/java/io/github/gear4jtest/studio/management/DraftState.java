package io.github.gear4jtest.studio.management;

import java.util.Set;

import io.github.gear4jtest.studio.model.DraftRevision;

public record DraftState(StudioDraft draft, DraftRevision revision, Set<Permission> permissions) {
    public DraftState {
        permissions = Set.copyOf(permissions);
    }
}
