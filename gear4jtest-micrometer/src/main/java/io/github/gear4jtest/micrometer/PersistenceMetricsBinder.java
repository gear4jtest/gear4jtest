package io.github.gear4jtest.micrometer;

import java.util.Objects;

import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers gauges backed by {@link DatabaseExecutionManager#snapshotStats()}.
 */
public final class PersistenceMetricsBinder {
    private PersistenceMetricsBinder() {
    }

    public static void bind(MeterRegistry meterRegistry, DatabaseExecutionManager manager) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(manager, "manager must not be null");
        Gauge.builder("gear4j.persistence.buffered.station.logs", manager,
                      value -> value.snapshotStats().bufferedStationLogs())
                .description("Buffered station log records waiting for persistence flush")
                .register(meterRegistry);
        Gauge.builder("gear4j.persistence.active.runs", manager,
                      value -> value.snapshotStats().activeRuns())
                .description("Number of active run buffers in the JDBC persistence manager")
                .register(meterRegistry);
    }
}
