package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

import io.github.gear4jtest.core.event.EventManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** Registers gauges backed by {@link EventManager#snapshotStats()}. */
public final class EventMetricsBinder {
    private EventMetricsBinder() {
    }

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
