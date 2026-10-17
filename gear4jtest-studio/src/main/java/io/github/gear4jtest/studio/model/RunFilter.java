package io.github.gear4jtest.studio.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Bounded time window; absence of a mode includes both TEST and RUN. */
public record RunFilter(Instant from,
                        Instant until,
                        String status,
                        String mode,
                        UUID versionId,
                        UUID engineRunId,
                        Long minimumDurationMillis) {
    public RunFilter {
        if (from == null || until == null || !from.isBefore(until)
                || Duration.between(from, until).compareTo(Duration.ofDays(31)) > 0
                || status != null && !RunObservation.STATUSES.contains(status)
                || mode != null && !Set.of("TEST", "RUN").contains(mode)
                || minimumDurationMillis != null && minimumDurationMillis < 0)
            throw new IllegalArgumentException("Invalid run filter");
    }
}
