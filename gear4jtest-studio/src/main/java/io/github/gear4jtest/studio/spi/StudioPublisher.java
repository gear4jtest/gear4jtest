package io.github.gear4jtest.studio.spi;

import java.util.Optional;
import java.util.Set;

import io.github.gear4jtest.studio.model.PublishedArtifact;
import io.github.gear4jtest.studio.model.StudioPublication;
import io.github.gear4jtest.studio.model.StudioVersion;

/**
 * Host-owned Gear4J lifecycle adapter. Mutations must be idempotent for an
 * identical assembly-line/version/mode/content identity, and must reject
 * conflicting content. No executor or host resource is owned by management.
 */
public interface StudioPublisher {
    Set<String> capabilities();

    void publishTest(StudioVersion version, String actor);

    void promoteToRun(StudioVersion version, String actor);

    Optional<PublishedArtifact> find(StudioVersion version, StudioPublication.Mode mode);
}
