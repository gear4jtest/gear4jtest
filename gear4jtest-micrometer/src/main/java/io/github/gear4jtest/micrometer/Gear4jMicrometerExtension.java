package io.github.gear4jtest.micrometer;

import java.util.Objects;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Micrometer lifecycle observer for Gear4J runs and station executions. */
public final class Gear4jMicrometerExtension implements RunLifecycleExtension, StationLifecycleExtension {
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public Gear4jMicrometerExtension(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.BEST_EFFORT;
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void onRunStarted(ExecutionContext ctx, AssemblyRunTrace run) {
        Counter.builder("gear4j.runs.started")
                .description("Number of Gear4J pipeline runs started")
                .tags("pipeline.id", safe(run.getPipelineId()))
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onRunCompleted(ExecutionContext ctx, AssemblyRunTrace run) {
        Counter.builder("gear4j.runs.completed")
                .description("Number of Gear4J pipeline runs completed")
                .tags("pipeline.id", safe(run.getPipelineId()), "status", safe(run.getStatus()))
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onStationStarted(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot) {
        Counter.builder("gear4j.stations.started")
                .description("Number of Gear4J station executions started")
                .tags(stationTagArray(snapshot))
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onStationCompleted(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot) {
        Counter.builder("gear4j.stations.completed")
                .description("Number of Gear4J station executions completed")
                .tags("operation.id", safe(snapshot.operationId()), "branch.id", safe(snapshot.branchId()),
                      "status", safe(snapshot.status()))
                .register(meterRegistry)
                .increment();
    }

    private static String[] stationTagArray(StationLogRecord snapshot) {
        return new String[] { "operation.id", safe(snapshot.operationId()), "branch.id", safe(snapshot.branchId()) };
    }

    private static String safe(Object value) {
        return value == null ? UNKNOWN : String.valueOf(value);
    }
}
