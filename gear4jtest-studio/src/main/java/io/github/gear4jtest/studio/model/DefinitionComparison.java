package io.github.gear4jtest.studio.model;

import java.util.UUID;

/**
 * XML is optional when a saved invalid definition cannot be represented by the
 * adapter.
 */
public record DefinitionComparison(UUID baseRevisionId, DefinitionDiff diff, String baseXml, String proposedXml) {}
