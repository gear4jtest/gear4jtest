package io.github.gear4jtest.experimental.cache.history;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.sidecompute.SideComputeHandler;
import io.github.gear4jtest.experimental.cache.assemblylinecache.AssemblyLineCacheRuntimeKeys;
import io.github.gear4jtest.experimental.cache.history.taskhistory.TaskHistoryResult;

public final class TaskHistoryExpirySideComputeHandler<T>
        implements SideComputeHandler<StationFinishedEvent, TaskHistoryResult<T>> {
    @Override
    public void handle(String sideComputeKey,
                       StationFinishedEvent event,
                       TaskHistoryResult<T> value,
                       ExecutionContext executionContext) {

        Object trackerObj = executionContext.getContext()
                .get(AssemblyLineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER);

        if (trackerObj instanceof ExpirableDependencyTracker tracker) {
            if (value == null || value.expiresAt() == null) {
                tracker.recordMissingExpiry("sidecompute:" + sideComputeKey);
            } else {
                tracker.recordConsumed("sidecompute:" + sideComputeKey, value.expiresAt());
            }
        }
    }
}
