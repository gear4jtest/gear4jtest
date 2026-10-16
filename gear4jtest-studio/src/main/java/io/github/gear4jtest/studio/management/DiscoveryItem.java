package io.github.gear4jtest.studio.management;

import java.util.UUID;

/**
 * DISCOVER reveals only identity, label and whether full details may be opened.
 */
public record DiscoveryItem(UUID id, ResourceKind kind, UUID parentId, String name, boolean readable) {}
