package io.github.gear4jtest.experimental.cache.history.taskhistory;

import java.time.Instant;
import java.util.Objects;

public record TaskHistoryResult<T>(T value, Instant expiresAt) {
    public TaskHistoryResult {
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
