package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import io.github.gear4jtest.external.api.artifact.ArtifactStoreMonitor;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers low-cardinality operation, byte, latency and failure store metrics.
 */
public final class ArtifactStoreMetricsBinder {
    private ArtifactStoreMetricsBinder() {
    }

    public static void bind(MeterRegistry meterRegistry, ArtifactStoreMonitor monitor) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(monitor, "monitor must not be null");
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.operations",
                        "Artifact-store operations by finite operation and outcome",
                        value -> value.snapshotStats().writesCompleted(), "operation", "write", "outcome",
                        "completed");
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.operations",
                        "Artifact-store operations by finite operation and outcome",
                        value -> value.snapshotStats().writeFailures(), "operation", "write", "outcome", "failed");
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.operations",
                        "Artifact-store operations by finite operation and outcome",
                        value -> value.snapshotStats().readStreamsCompleted(), "operation", "read", "outcome",
                        "completed");
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.operations",
                        "Artifact-store operations by finite operation and outcome",
                        value -> value.snapshotStats().readStreamsClosedEarly(), "operation", "read", "outcome",
                        "closed_early");
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.operations",
                        "Artifact-store operations by finite operation and outcome",
                        value -> value.snapshotStats().readFailures(), "operation", "read", "outcome", "failed");

        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.bytes",
                        "Artifact bytes processed by finite store operation",
                        value -> value.snapshotStats().bytesWritten(), "operation", "write");
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.bytes",
                        "Artifact bytes processed by finite store operation",
                        value -> value.snapshotStats().bytesRead(), "operation", "read");
        registerTimer(meterRegistry, monitor, "write",
                      value -> value.snapshotStats().writesCompleted() + value.snapshotStats().writeFailures(),
                      value -> value.snapshotStats().writeDurationNanos());
        registerTimer(meterRegistry, monitor, "read",
                      value -> value.snapshotStats().readStreamsCompleted()
                              + value.snapshotStats().readStreamsClosedEarly()
                              + value.snapshotStats().readFailures(),
                      value -> value.snapshotStats().readDurationNanos());
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.store.cleanup.failures",
                        "Artifact-store temporary-file cleanup failures",
                        value -> value.snapshotStats().cleanupFailures());
    }

    private static void registerTimer(MeterRegistry meterRegistry,
                                      ArtifactStoreMonitor monitor,
                                      String operation,
                                      ToLongFunction<ArtifactStoreMonitor> countFunction,
                                      ToDoubleFunction<ArtifactStoreMonitor> durationFunction) {
        FunctionTimer.builder("gear4j.artifacts.store.operation.duration", monitor, countFunction,
                              durationFunction, TimeUnit.NANOSECONDS)
                .description("Cumulative artifact-store operation duration")
                .tag("operation", operation)
                .register(meterRegistry);
    }

    private static void registerCounter(MeterRegistry meterRegistry,
                                        ArtifactStoreMonitor monitor,
                                        String name,
                                        String description,
                                        ToDoubleFunction<ArtifactStoreMonitor> valueFunction,
                                        String... tags) {
        FunctionCounter.builder(name, monitor, valueFunction)
                .description(description)
                .tags(tags)
                .register(meterRegistry);
    }
}
