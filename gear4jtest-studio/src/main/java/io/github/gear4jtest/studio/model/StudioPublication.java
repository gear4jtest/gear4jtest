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
                                String errorCode) {
    public enum Mode {
        TEST, RUN
    }

    public enum State {
        REQUESTED, CONFIRMED, REJECTED, UNKNOWN
    }
}
