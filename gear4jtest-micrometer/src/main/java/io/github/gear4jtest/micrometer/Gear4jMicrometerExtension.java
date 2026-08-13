package io.github.gear4jtest.micrometer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Micrometer lifecycle observer for Gear4J runs and station executions. */
public final class Gear4jMicrometerExtension implements RunLifecycleExtension, StationLifecycleExtension {
    private final MeterRegistry meterRegistry;
    private final Gear4jMeterTagPolicy tagPolicy;

    public Gear4jMicrometerExtension(MeterRegistry meterRegistry) {
        this(meterRegistry, Gear4jMeterTagPolicy.defaults());
    }

    public Gear4jMicrometerExtension(MeterRegistry meterRegistry, Gear4jMeterTagPolicy tagPolicy) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.tagPolicy = Objects.requireNonNull(tagPolicy, "tagPolicy must not be null");
    }

    @Override
    public LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.BEST_EFFORT;
    }

    @Override
    public int getOrder() {
        return RuntimeExtension.TERMINAL_OBSERVER_ORDER;
    }

    @Override
    public void onRunStarted(ExecutionContext ctx, RunTrace run) {
        Counter.builder("gear4j.runs.started")
                .description("Number of Gear4J assembly line runs started")
                .tags(tagPolicy.runStartedTags(run))
                .register(meterRegistry)
                .increment();
    }

    @Override
    public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
        String[] tags = tagPolicy.runCompletedTags(run);
        Counter.builder("gear4j.runs.completed")
                .description("Number of Gear4J assembly line runs completed")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        recordTimer("gear4j.runs.duration", "Duration of completed Gear4J assembly line runs", run.getStartTime(),
                    run.getEndTime(), tags);
    }

    @Override
    public void onStationStarted(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot) {
        Counter.builder("gear4j.stations.started")
                .description("Number of Gear4J station executions started")
                .tags(tagPolicy.stationStartedTags(snapshot))
                .register(meterRegistry)
                .increment();
        if (isBranch(snapshot)) {
            Counter.builder("gear4j.branches.started")
                    .description("Number of Gear4J container branches that started execution")
                    .tags(tagPolicy.stationStartedTags(snapshot))
                    .register(meterRegistry)
                    .increment();
        }
    }

    @Override
    public void onStationCompleted(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot) {
        String[] tags = tagPolicy.stationCompletedTags(snapshot);
        Counter.builder("gear4j.stations.completed")
                .description("Number of Gear4J station executions completed")
                .tags(tags)
                .register(meterRegistry)
                .increment();
        recordTimer("gear4j.stations.duration", "Duration of completed Gear4J station executions",
                    snapshot.startedAt(), snapshot.endedAt(), tags);
        if (isBranch(snapshot)) {
            recordBranchCompletion(tags);
            recordTimer("gear4j.branches.duration", "Duration of completed Gear4J container branches",
                        snapshot.startedAt(), snapshot.endedAt(), tags);
        }
    }

    @Override
    public void onStationSkipped(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot,
                                 StationSkipReason reason) {
        recordBranchCompletion(snapshot);
    }

    @Override
    public void onStationCancelled(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot,
                                   StationCancellationReason reason,
                                   Exception error) {
        recordBranchCompletion(snapshot);
    }

    @Override
    public void onStationInterrupted(ExecutionContext runCtx,
                                     StationExecutionContext stationCtx,
                                     StationLogRecord snapshot,
                                     StationInterruptionReason reason,
                                     String interruptingOperationId,
                                     Exception error) {
        recordBranchCompletion(snapshot);
    }

    @Override
    public void onStationFailedBeforeStart(ExecutionContext runCtx,
                                           StationExecutionContext stationCtx,
                                           StationLogRecord snapshot,
                                           Exception error) {
        recordBranchCompletion(snapshot);
        if (isBranch(snapshot) && error instanceof RejectedExecutionException) {
            Counter.builder("gear4j.branches.rejected")
                    .description("Number of Gear4J container branches rejected before execution")
                    .tags(tagPolicy.stationStartedTags(snapshot))
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void recordBranchCompletion(StationLogRecord snapshot) {
        if (!isBranch(snapshot)) {
            return;
        }
        recordBranchCompletion(tagPolicy.stationCompletedTags(snapshot));
    }

    private void recordBranchCompletion(String[] tags) {
        Counter.builder("gear4j.branches.completed")
                .description("Number of Gear4J container branches with a terminal outcome")
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    private boolean isBranch(StationLogRecord snapshot) {
        return snapshot != null && snapshot.branchId() != null;
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
}
