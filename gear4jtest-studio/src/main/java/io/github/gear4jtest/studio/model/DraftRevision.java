package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot; number is scoped to its draft, never a published version.
 */
public record DraftRevision(UUID draftId,
                            UUID revisionId,
                            long number,
                            String author,
                            Instant savedAt,
                            Origin origin,
                            ChainDefinition definition) {
    public enum Origin {
        STUDIO, XML_IMPORT
    }
}
