package io.github.gear4jtest.micrometer;

import java.util.Locale;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

import io.github.gear4jtest.core.persistence.PersistenceFlushObservation;
import io.github.gear4jtest.core.persistence.PersistenceFlushSubscription;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Registers persistence gauges plus active flush-duration observations. */
public final class PersistenceMetricsBinder {
    private PersistenceMetricsBinder() {
    }

    public static void bind(MeterRegistry meterRegistry, PersistenceRuntimeMonitor manager) {
        bindWithSubscription(meterRegistry, manager);
    }

    /**
     * Registers persistence meters and returns the removable active-observation
     * subscription.
     */
    public static PersistenceFlushSubscription bindWithSubscription(MeterRegistry meterRegistry,
                                                                    PersistenceRuntimeMonitor manager) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(manager, "manager must not be null");
        registerGauge(meterRegistry, manager, "gear4j.persistence.buffered.station.logs",
                      "Buffered station log records waiting for persistence flush",
                      value -> value.snapshotStats().bufferedStationLogs());
        registerGauge(meterRegistry, manager, "gear4j.persistence.buffered.station.logs.oldest.age.seconds",
                      "Age in seconds of the oldest station log waiting for persistence flush",
                      value -> value.snapshotStats().oldestBufferedStationLogAge().toMillis() / 1_000.0d);
        registerGauge(meterRegistry, manager, "gear4j.persistence.active.runs",
                      "Number of active run buffers in the JDBC persistence manager",
                      value -> value.snapshotStats().activeRuns());
        registerGauge(meterRegistry, manager, "gear4j.persistence.flushes.scheduled",
                      "Number of JDBC persistence flushes scheduled since manager startup",
                      value -> value.snapshotStats().scheduledFlushes());
        registerGauge(meterRegistry, manager, "gear4j.persistence.flushes.completed",
                      "Number of JDBC persistence flushes completed since manager startup",
                      value -> value.snapshotStats().completedFlushes());
        registerGauge(meterRegistry, manager, "gear4j.persistence.flushes.failed",
                      "Number of JDBC persistence flushes failed since manager startup",
                      value -> value.snapshotStats().failedFlushes());
        registerGauge(meterRegistry, manager, "gear4j.persistence.appends.rejected",
                      "Number of station log append attempts rejected because the persistence buffer was full",
                      value -> value.snapshotStats().rejectedAppends());
        PersistenceFlushSubscription subscription = manager.subscribeToFlushes(observation -> recordFlush(
                                                                                                          meterRegistry,
                                                                                                          observation));
        return subscription != null ? subscription : () -> {
            // Defensive compatibility for proxy implementations returning null.
        };
    }

    private static void recordFlush(MeterRegistry meterRegistry, PersistenceFlushObservation observation) {
        Timer.builder("gear4j.persistence.flush.duration")
                .description("End-to-end duration of completed JDBC persistence flush attempts")
                .tags("trigger", tag(observation.trigger()), "outcome", tag(observation.outcome()))
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(observation.duration());
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static void registerGauge(MeterRegistry meterRegistry,
                                      PersistenceRuntimeMonitor manager,
                                      String name,
                                      String description,
                                      ToDoubleFunction<PersistenceRuntimeMonitor> valueFunction) {
        Gauge.builder(name, manager, valueFunction)
                .description(description)
                .register(meterRegistry);
    }
}
