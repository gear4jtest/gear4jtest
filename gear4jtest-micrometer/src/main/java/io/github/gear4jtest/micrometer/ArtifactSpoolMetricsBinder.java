package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.function.ToDoubleFunction;

import io.github.gear4jtest.external.api.artifact.ArtifactSpoolMonitor;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Registers tag-free occupancy, cleanup and quota metrics for artifact spools.
 */
public final class ArtifactSpoolMetricsBinder {
    private ArtifactSpoolMetricsBinder() {
    }

    public static void bind(MeterRegistry meterRegistry, ArtifactSpoolMonitor monitor) {
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Objects.requireNonNull(monitor, "monitor must not be null");
        registerGauge(meterRegistry, monitor, "gear4j.artifacts.spool.files",
                      "Temporary artifact files currently retained in the private spool",
                      value -> value.snapshotSpoolStats().currentFiles());
        registerGauge(meterRegistry, monitor, "gear4j.artifacts.spool.bytes",
                      "Temporary artifact bytes currently retained in the private spool",
                      value -> value.snapshotSpoolStats().currentBytes());
        registerGauge(meterRegistry, monitor, "gear4j.artifacts.spool.capacity.bytes",
                      "Configured hard byte quota of the private artifact spool",
                      value -> value.snapshotSpoolStats().maxBytes());
        registerGauge(meterRegistry, monitor, "gear4j.artifacts.spool.instances",
                      "Live JVM-local spool instances sharing the directory-scoped quota",
                      value -> value.snapshotSpoolStats().activeInstances());
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.spool.stale.files.deleted",
                        "Stale temporary artifact files deleted from the private spool",
                        value -> value.snapshotSpoolStats().staleFilesDeleted());
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.spool.stale.bytes.deleted",
                        "Stale temporary artifact bytes deleted from the private spool",
                        value -> value.snapshotSpoolStats().staleBytesDeleted());
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.spool.quota.rejections",
                        "Temporary artifact writes rejected by the private spool byte quota",
                        value -> value.snapshotSpoolStats().quotaRejections());
        registerCounter(meterRegistry, monitor, "gear4j.artifacts.spool.cleanup.failures",
                        "Private artifact-spool cleanup failures",
                        value -> value.snapshotSpoolStats().cleanupFailures());
    }

    private static void registerGauge(MeterRegistry meterRegistry,
                                      ArtifactSpoolMonitor monitor,
                                      String name,
                                      String description,
                                      ToDoubleFunction<ArtifactSpoolMonitor> valueFunction) {
        Gauge.builder(name, monitor, valueFunction)
                .description(description)
                .register(meterRegistry);
    }

    private static void registerCounter(MeterRegistry meterRegistry,
                                        ArtifactSpoolMonitor monitor,
                                        String name,
                                        String description,
                                        ToDoubleFunction<ArtifactSpoolMonitor> valueFunction) {
        FunctionCounter.builder(name, monitor, valueFunction)
                .description(description)
                .register(meterRegistry);
    }
}
