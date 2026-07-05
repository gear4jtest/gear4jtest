package io.github.gear4jtest.experimental.cache.history.taskhistory;

import java.util.Objects;

import io.github.gear4jtest.experimental.cache.history.CacheTrackerContext;
import io.github.gear4jtest.experimental.cache.history.ExpirableDependencyTracker;

public class TrackingTaskHistoryApi implements TaskHistoryApi {
    private final RawTaskHistoryApi delegate;

    public TrackingTaskHistoryApi(RawTaskHistoryApi delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public <T> TaskHistoryResult<T> get(String key, Class<T> type) {
        TaskHistoryResult<T> result = delegate.get(key, type);

        ExpirableDependencyTracker tracker = CacheTrackerContext.get();
        if (tracker != null) {
            if (result == null || result.expiresAt() == null) {
                tracker.recordMissingExpiry("taskhistory:" + key);
            } else {
                tracker.recordConsumed("taskhistory:" + key, result.expiresAt());
            }
        }

        return result;
    }
}
