package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

import io.github.gear4jtest.core.execution.PersistenceRuntimeMonitor;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers gauges backed by {@link PersistenceRuntimeMonitor#snapshotStats()}.
 */
public final class PersistenceMetricsBinder {
    private PersistenceMetricsBinder() {
    }

    public static void bind(MeterRegistry meterRegistry, PersistenceRuntimeMonitor manager) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(manager, "manager must not be null");
        registerGauge(meterRegistry, manager, "gear4j.persistence.buffered.station.logs",
                      "Buffered station log records waiting for persistence flush",
                      value -> value.snapshotStats().bufferedStationLogs());
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
