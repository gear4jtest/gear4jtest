package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.github.gear4jtest.studio.service.StudioProblem;

/**
 * Immutable snapshot; number is scoped to its draft, never a published version.
 */
public record DraftRevision(UUID draftId,
                            UUID revisionId,
                            long number,
                            String author,
                            Instant savedAt,
                            Origin origin,
                            Kind kind,
                            ChainDefinition definition,
                            Source source) {
    public enum Kind {
        STRUCTURED, SOURCE
    }

    /** Exact source text, never a partially recovered definition. */
    public record Source(String mediaType, String text) {
        public Source {
            Objects.requireNonNull(mediaType);
            Objects.requireNonNull(text);
        }
    }

    public DraftRevision {
        Objects.requireNonNull(kind);
        if (kind == Kind.STRUCTURED ? definition == null || source != null : source == null || definition != null)
            throw new IllegalArgumentException("Exactly one revision representation is required");
    }

    public DraftRevision(UUID draftId,
                         UUID revisionId,
                         long number,
                         String author,
                         Instant savedAt,
                         Origin origin,
                         ChainDefinition definition) {
        this(draftId, revisionId, number, author, savedAt, origin, Kind.STRUCTURED, definition, null);
    }

    /** A stored source must be explicitly analyzed and saved as structured work. */
    public ChainDefinition requireDefinition() {
        if (kind != Kind.STRUCTURED)
            throw new StudioProblem(StudioProblem.Code.SOURCE_REQUIRES_ANALYSIS);
        return definition;
    }

    public enum Origin {
        STUDIO, XML_IMPORT
    }
}
