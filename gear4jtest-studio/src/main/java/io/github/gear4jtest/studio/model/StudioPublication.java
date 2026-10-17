package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Durable intent and observed result for one immutable version and Gear4J mode.
 */
public record StudioPublication(UUID versionId,
                                Mode mode,
                                State state,
                                String requestedBy,
                                Instant requestedAt,
                                Instant completedAt,
                                String publishedBy,
                                Instant publishedAt,
                                String errorCode,
                                UUID attemptId) {
    public StudioPublication {
        java.util.Objects.requireNonNull(attemptId);
    }

    public StudioPublication(UUID versionId,
                             Mode mode,
                             State state,
                             String requestedBy,
                             Instant requestedAt,
                             Instant completedAt,
                             String publishedBy,
                             Instant publishedAt,
                             String errorCode) {
        this(versionId, mode, state, requestedBy, requestedAt, completedAt, publishedBy, publishedAt, errorCode,
                UUID.randomUUID());
    }

    public enum Mode {
        TEST, RUN
    }

    public enum State {
        REQUESTED, CONFIRMED, REJECTED, UNKNOWN
    }
}
