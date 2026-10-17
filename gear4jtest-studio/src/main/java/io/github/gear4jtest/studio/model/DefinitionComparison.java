package io.github.gear4jtest.studio.model;

import java.util.UUID;

/**
 * XML is optional when a saved invalid definition cannot be represented by the
 * adapter. A null diff explicitly denotes a textual comparison involving a
 * SOURCE revision; XML fields then contain its exact text, never a projection.
 */
public record DefinitionComparison(UUID baseRevisionId, DefinitionDiff diff, String baseXml, String proposedXml) {}
