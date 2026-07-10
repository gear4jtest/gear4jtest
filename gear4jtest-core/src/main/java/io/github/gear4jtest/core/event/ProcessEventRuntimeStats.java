package io.github.gear4jtest.core.event;

import io.github.gear4jtest.core.api.annotation.PublicApi;

/** Process-wide, tag-free aggregation of all in-memory event runtimes. */
@PublicApi
public record ProcessEventRuntimeStats(int activeRuntimes,
                                       int queuedEvents,
                                       int inFlightReactions,
                                       long publishedEvents,
                                       long dispatchedEvents,
                                       long submittedReactions,
                                       long completedReactions,
                                       long droppedEvents,
                                       long droppedReactions,
                                       long failedReactions,
                                       long dispatcherRejectedTasks,
                                       long dispatchLatencySamples,
                                       long totalDispatchLatencyNanos,
                                       long maxDispatchLatencyNanos) {
    public double averageDispatchLatencyNanos() {
        return dispatchLatencySamples == 0 ? 0.0 : (double) totalDispatchLatencyNanos / dispatchLatencySamples;
    }
}
