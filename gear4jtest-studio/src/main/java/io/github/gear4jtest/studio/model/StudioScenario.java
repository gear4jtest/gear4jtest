package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.UUID;

/** Scenario metadata inherits its chain/draft ACL; revisions are immutable. */
public record StudioScenario(UUID id,
                             UUID resourceId,
                             UUID chainId,
                             String name,
                             String owner,
                             long version,
                             long head,
                             boolean archived,
                             Instant createdAt,
                             Instant updatedAt) {}
