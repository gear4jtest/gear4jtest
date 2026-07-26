package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

import io.github.gear4jtest.core.api.annotation.Internal;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.event.EventRuntimeMetrics;
import io.github.gear4jtest.core.event.ProcessEventRuntimeStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Registers gauges backed by {@link EventManager#snapshotStats()}. */
public final class EventMetricsBinder {
    private EventMetricsBinder() {
    }

    @Internal
    public static void bind(MeterRegistry meterRegistry, EventManager manager) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(manager, "manager must not be null");
        registerGauge(meterRegistry, manager, "gear4j.events.published",
                      "Number of events accepted by the in-memory event runtime",
                      value -> value.snapshotStats().publishedEvents());
        registerGauge(meterRegistry, manager, "gear4j.events.dispatched",
                      "Number of events drained by the in-memory event dispatcher",
                      value -> value.snapshotStats().dispatchedEvents());
        registerGauge(meterRegistry, manager, "gear4j.events.dropped",
                      "Number of events rejected because the in-memory event queue was full",
                      value -> value.snapshotStats().droppedEvents());
        registerGauge(meterRegistry, manager, "gear4j.events.queued",
                      "Current number of events waiting in the in-memory event dispatcher queue",
                      value -> value.snapshotStats().queuedEvents());
        registerGauge(meterRegistry, manager, "gear4j.events.queue.remaining.capacity",
                      "Current remaining capacity of the bounded in-memory event dispatcher queue",
                      value -> value.snapshotStats().remainingEventQueueCapacity());
        registerGauge(meterRegistry, manager, "gear4j.reactions.submitted",
                      "Number of event reactions submitted to the configured reaction executor",
                      value -> value.snapshotStats().submittedReactions());
        registerGauge(meterRegistry, manager, "gear4j.reactions.completed",
                      "Number of event reactions completed by the configured reaction executor",
                      value -> value.snapshotStats().completedReactions());
        registerGauge(meterRegistry, manager, "gear4j.reactions.dropped",
                      "Number of event reactions dropped before execution",
                      value -> value.snapshotStats().droppedReactions());
        registerGauge(meterRegistry, manager, "gear4j.reactions.failed",
                      "Number of event reactions that failed while executing",
                      value -> value.snapshotStats().failedReactions());
        registerGauge(meterRegistry, manager, "gear4j.reactions.pending",
                      "Current number of accepted event reactions that have not reached a terminal state yet",
                      value -> value.snapshotStats().pendingReactions());
        registerGauge(meterRegistry, manager, "gear4j.reactions.in.flight",
                      "Current number of event reactions executing in the configured reaction executor",
                      value -> value.snapshotStats().inFlightReactions());
    }

    /**
     * Registers tag-free gauges aggregated across every event runtime in this JVM.
     */
    public static void bindProcessWide(MeterRegistry meterRegistry) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        registerProcessGauge(meterRegistry, "gear4j.events.process.active.runtimes",
                             "Current number of active in-memory event runtimes",
                             ProcessEventRuntimeStats::activeRuntimes);
        registerProcessGauge(meterRegistry, "gear4j.events.process.queued",
                             "Current number of queued events across all runtimes",
                             ProcessEventRuntimeStats::queuedEvents);
        registerProcessGauge(meterRegistry, "gear4j.events.process.dropped",
                             "Number of events dropped across all runtimes", ProcessEventRuntimeStats::droppedEvents);
        registerProcessGauge(meterRegistry, "gear4j.reactions.process.dropped",
                             "Number of reactions dropped across all runtimes",
                             ProcessEventRuntimeStats::droppedReactions);
        registerProcessGauge(meterRegistry, "gear4j.events.process.dispatcher.rejected",
                             "Number of tasks rejected by the shared event dispatcher",
                             ProcessEventRuntimeStats::dispatcherRejectedTasks);
        registerProcessGauge(meterRegistry, "gear4j.events.process.dispatch.latency.average.nanos",
                             "Average event queue-to-dispatch latency in nanoseconds",
                             ProcessEventRuntimeStats::averageDispatchLatencyNanos);
        registerProcessGauge(meterRegistry, "gear4j.events.process.dispatch.latency.max.nanos",
                             "Maximum observed event queue-to-dispatch latency in nanoseconds",
                             ProcessEventRuntimeStats::maxDispatchLatencyNanos);
    }

    private static void registerProcessGauge(MeterRegistry meterRegistry,
                                             String name,
                                             String description,
                                             ToDoubleFunction<ProcessEventRuntimeStats> valueFunction) {
        Gauge.builder(name, EventRuntimeMetrics.class,
                      ignored -> valueFunction.applyAsDouble(EventRuntimeMetrics.snapshot()))
                .description(description)
                .register(meterRegistry);
    }

    private static void registerGauge(MeterRegistry meterRegistry,
                                      EventManager manager,
                                      String name,
                                      String description,
                                      ToDoubleFunction<EventManager> valueFunction) {
        Gauge.builder(name, manager, valueFunction)
                .description(description)
                .register(meterRegistry);
    }
}
