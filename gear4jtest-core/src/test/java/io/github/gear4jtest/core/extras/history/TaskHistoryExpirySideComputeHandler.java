package io.github.gear4jtest.core.extras.history;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.event.StationFinishedEvent;
import io.github.gear4jtest.core.extras.history.taskhistory.TaskHistoryResult;
import io.github.gear4jtest.core.extras.pipelinecache.PipelineCacheRuntimeKeys;
import io.github.gear4jtest.core.sidecompute.SideComputeHandler;

public final class TaskHistoryExpirySideComputeHandler<T>
        implements SideComputeHandler<StationFinishedEvent, TaskHistoryResult<T>> {

    @Override
    public void handle(String sideComputeKey,
                       StationFinishedEvent event,
                       TaskHistoryResult<T> value,
                       ExecutionContext executionContext) {

        Object trackerObj = executionContext.getContext().get(PipelineCacheRuntimeKeys.EXPIRABLE_DEPENDENCY_TRACKER);

        if (trackerObj instanceof ExpirableDependencyTracker tracker) {
            if (value == null || value.expiresAt() == null) {
                tracker.recordMissingExpiry("sidecompute:" + sideComputeKey);
            } else {
                tracker.recordConsumed("sidecompute:" + sideComputeKey, value.expiresAt());
            }
        }
    }
}
