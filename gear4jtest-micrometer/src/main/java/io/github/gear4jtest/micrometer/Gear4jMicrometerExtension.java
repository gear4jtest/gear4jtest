package io.github.gear4jtest.micrometer;

import java.time.Duration;
import java.time.Instant;
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
import io.micrometer.core.instrument.Timer;

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
        String[] tags = { "pipeline.id", safe(run.getPipelineId()), "status", safe(run.getStatus()) };
        Counter.builder("gear4j.runs.completed")
                .description("Number of Gear4J pipeline runs completed")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        recordTimer("gear4j.runs.duration", "Duration of completed Gear4J pipeline runs", run.getStartTime(),
                    run.getEndTime(), tags);
    }

    @Override
    public void onStationStarted(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot) {
        Counter.builder("gear4j.stations.started")
                .description("Number of Gear4J station executions started")
                .tags(stationStartedTags(snapshot))
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onStationCompleted(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot) {
        String[] tags = stationCompletedTags(snapshot);
        Counter.builder("gear4j.stations.completed")
                .description("Number of Gear4J station executions completed")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        recordTimer("gear4j.stations.duration", "Duration of completed Gear4J station executions",
                    snapshot.startedAt(), snapshot.endedAt(), tags);
    }

    private void recordTimer(String name, String description, Instant start, Instant end, String... tags) {
        if (start == null || end == null || end.isBefore(start)) {
            return;
        }
        Timer.builder(name)
                .description(description)
                .tags(tags)
                .register(meterRegistry)
                .record(Duration.between(start, end));
    }

    private static String[] stationStartedTags(StationLogRecord snapshot) {
        return new String[] { "operation.id", safe(snapshot.operationId()), "branch.id", safe(snapshot.branchId()) };
    }

    private static String[] stationCompletedTags(StationLogRecord snapshot) {
        return new String[] { "operation.id", safe(snapshot.operationId()), "branch.id", safe(snapshot.branchId()),
                "status", safe(snapshot.status()) };
    }

    private static String safe(Object value) {
        return value == null ? UNKNOWN : String.valueOf(value);
    }
}
