package io.github.gear4jtest.studio.model;

import java.time.Instant;

/**
 * Publication observed in Gear4J, not evidence of loading by every application
 * instance.
 */
public record PublishedArtifact(String assemblyLineId,
                                String version,
                                StudioPublication.Mode mode,
                                String artifactHash,
                                String mediaType,
                                long sizeBytes,
                                String publishedBy,
                                Instant publishedAt) {}
