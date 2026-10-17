package io.github.gear4jtest.studio.model;

import java.util.List;
import java.util.Set;

/** Current management view; authorization is rechecked on every mutation. */
public record VersionState(StudioVersion version, List<StudioPublication> publications, Set<String> actions) {
    public VersionState {
        publications = List.copyOf(publications);
        actions = Set.copyOf(actions);
    }
}
