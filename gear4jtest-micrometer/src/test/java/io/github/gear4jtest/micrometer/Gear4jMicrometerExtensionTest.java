package io.github.gear4jtest.micrometer;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Gear4jMicrometerExtensionTest {
    @Test
    void should_increment_run_counters_and_record_duration() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry);
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "checkout", Map.of());
        run.setStartTime(Instant.parse("2026-06-08T10:15:30Z"));
        run.setEndTime(Instant.parse("2026-06-08T10:15:32Z"));
        run.setStatus(ExecutionStatus.SUCCEEDED);

        // When
        extension.onRunStarted(null, run);
        extension.onRunCompleted(null, run);

        // Then
        assertThat(meterRegistry.counter("gear4j.runs.started").count())
                .as("started runs counter")
                .isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.runs.completed", "status", "SUCCEEDED")
                .count())
                .as("completed runs counter")
                .isEqualTo(1.0d);
        double durationMillis = meterRegistry.timer("gear4j.runs.duration", "status", "SUCCEEDED")
                .totalTime(TimeUnit.MILLISECONDS);
        assertThat(durationMillis)
                .as("completed run duration")
                .isEqualTo(2000.0d);
    }

    @Test
    void should_increment_station_counters_and_record_duration() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry);
        StationLogRecord snapshot = new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "normalize", null,
                "branch-a", StationLogStatus.SUCCEEDED, Instant.parse("2026-06-08T10:15:30Z"),
                Instant.parse("2026-06-08T10:15:31Z"), null, null, Map.of(), "item-1");

        // When
        extension.onStationStarted(null, null, snapshot);
        extension.onStationCompleted(null, null, snapshot);

        // Then
        assertThat(meterRegistry.counter("gear4j.stations.started")
                .count())
                .as("started stations counter")
                .isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.stations.completed", "status", "SUCCEEDED")
                .count())
                .as("completed stations counter")
                .isEqualTo(1.0d);
        double durationMillis = meterRegistry.timer("gear4j.stations.duration", "status", "SUCCEEDED")
                .totalTime(TimeUnit.MILLISECONDS);
        assertThat(durationMillis)
                .as("completed station duration")
                .isEqualTo(1000.0d);
        assertThat(meterRegistry.counter("gear4j.branches.started").count()).isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.branches.completed", "status", "SUCCEEDED").count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry.timer("gear4j.branches.duration", "status", "SUCCEEDED")
                .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(1000.0d);
    }

    @Test
    void should_record_synthetic_branch_outcomes_and_executor_rejections() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry);
        StationLogRecord skipped = stationWithStatus("branch-skipped", StationLogStatus.SKIPPED);
        StationLogRecord cancelled = stationWithStatus("branch-cancelled", StationLogStatus.CANCELLED);
        StationLogRecord interrupted = stationWithStatus("branch-interrupted", StationLogStatus.CANCELLED);
        StationLogRecord rejected = stationWithStatus("branch-rejected", StationLogStatus.FAILED);

        // When
        extension.onStationSkipped(null, null, skipped, StationSkipReason.CONDITION_NOT_SATISFIED);
        extension.onStationCancelled(null, null, cancelled, StationCancellationReason.TIMEOUT,
                                     new RuntimeException("timeout"));
        extension.onStationInterrupted(null, null, interrupted, StationInterruptionReason.SIBLING_FLOW_INTERRUPTED,
                                       "sibling", new RuntimeException("interrupted"));
        extension.onStationFailedBeforeStart(null, null, rejected, new RejectedExecutionException("full"));

        // Then
        assertThat(meterRegistry.counter("gear4j.branches.completed", "status", "SKIPPED").count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.branches.completed", "status", "CANCELLED").count())
                .isEqualTo(2.0d);
        assertThat(meterRegistry.counter("gear4j.branches.completed", "status", "FAILED").count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.branches.rejected").count()).isEqualTo(1.0d);
        assertThat(meterRegistry.find("gear4j.branches.duration").timer())
                .as("synthetic branches have no execution duration")
                .isNull();
    }

    @Test
    void should_use_custom_tag_policy() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMeterTagPolicy tagPolicy = new Gear4jMeterTagPolicy() {
            @Override
            public String[] runStartedTags(RunTrace run) {
                return new String[] { "scope", "low-cardinality" };
            }

            @Override
            public String[] runCompletedTags(RunTrace run) {
                return new String[] { "scope", "low-cardinality", "status",
                        Gear4jMeterTagPolicy.safe(run.getStatus()) };
            }

            @Override
            public String[] stationStartedTags(StationLogRecord station) {
                return new String[] { "scope", "low-cardinality" };
            }

            @Override
            public String[] stationCompletedTags(StationLogRecord station) {
                return new String[] { "scope", "low-cardinality", "status",
                        Gear4jMeterTagPolicy.safe(station.status()) };
            }
        };
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry, tagPolicy);
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "dynamic-user-pipeline", Map.of());

        // When
        extension.onRunStarted(null, run);

        // Then
        assertThat(meterRegistry.counter("gear4j.runs.started", "scope", "low-cardinality").count())
                .as("custom tag policy should control emitted tags")
                .isEqualTo(1.0d);
        assertThat(meterRegistry.find("gear4j.runs.started").tag("pipeline.id", "dynamic-user-pipeline").counter())
                .as("default high-cardinality pipeline tag should not be emitted by custom policy")
                .isNull();
    }

    @Test
    void default_policy_shouldKeepMeterCardinalityBoundedForDynamicIdentifiers() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry);

        // When
        for (int index = 0; index < 1_000; index++) {
            AssemblyRunTrace run = completedRun("pipeline-" + index);
            StationLogRecord station = completedStation("operation-" + index, "branch-" + index);
            extension.onRunStarted(null, run);
            extension.onRunCompleted(null, run);
            extension.onStationStarted(null, null, station);
            extension.onStationCompleted(null, null, station);
        }

        // Then
        assertThat(meterCount(meterRegistry, "gear4j.runs.started")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.runs.completed")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.runs.duration")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.stations.started")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.stations.completed")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.stations.duration")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.branches.started")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.branches.completed")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.branches.duration")).isEqualTo(1);
    }

    @Test
    void default_policy_shouldKeepRejectedBranchCardinalityBounded() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry);

        for (int index = 0; index < 1_000; index++) {
            StationLogRecord rejected = stationWithStatus("dynamic-branch-" + index, StationLogStatus.FAILED);
            extension.onStationFailedBeforeStart(null, null, rejected, new RejectedExecutionException("full"));
        }

        assertThat(meterCount(meterRegistry, "gear4j.branches.rejected")).isEqualTo(1);
        assertThat(meterCount(meterRegistry, "gear4j.branches.completed")).isEqualTo(1);
    }

    @Test
    void allowlist_policy_shouldCollapseUnknownIdentifiersIntoOneSeries() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMeterTagPolicy tagPolicy = Gear4jMeterTagPolicy.allowlistedIdentifiers(Set.of("checkout"),
                                                                                     Set.of("normalize"),
                                                                                     Set.of("main"));
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry, tagPolicy);

        // When
        extension.onRunStarted(null, completedRun("checkout"));
        extension.onRunStarted(null, completedRun("dynamic-a"));
        extension.onRunStarted(null, completedRun("dynamic-b"));
        extension.onStationStarted(null, null, completedStation("normalize", "main"));
        extension.onStationStarted(null, null, completedStation("dynamic-a", "dynamic-a"));
        extension.onStationStarted(null, null, completedStation("dynamic-b", "dynamic-b"));

        // Then
        assertThat(meterRegistry.counter("gear4j.runs.started", "pipeline.id", "checkout").count())
                .isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.runs.started", "pipeline.id", "other").count())
                .isEqualTo(2.0d);
        assertThat(meterRegistry.counter("gear4j.stations.started", "operation.id", "normalize", "branch.id",
                                         "main")
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.stations.started", "operation.id", "other", "branch.id",
                                         "other")
                .count()).isEqualTo(2.0d);
    }

    @Test
    void should_not_record_duration_when_timestamps_are_incomplete() {
        // Given
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Gear4jMicrometerExtension extension = new Gear4jMicrometerExtension(meterRegistry);
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "checkout", Map.of());
        run.setStatus(ExecutionStatus.FAILED);

        // When
        extension.onRunCompleted(null, run);

        // Then
        assertThat(meterRegistry.find("gear4j.runs.duration").timer())
                .as("duration timer is not registered without complete timestamps")
                .isNull();
    }

    private static AssemblyRunTrace completedRun(String pipelineId) {
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), pipelineId, Map.of());
        run.setStartTime(Instant.parse("2026-06-08T10:15:30Z"));
        run.setEndTime(Instant.parse("2026-06-08T10:15:31Z"));
        run.setStatus(ExecutionStatus.SUCCEEDED);
        return run;
    }

    private static StationLogRecord completedStation(String operationId, String branchId) {
        return new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), operationId, null, branchId,
                StationLogStatus.SUCCEEDED, Instant.parse("2026-06-08T10:15:30Z"),
                Instant.parse("2026-06-08T10:15:31Z"), null, null, Map.of(), "item-1");
    }

    private static StationLogRecord stationWithStatus(String branchId, StationLogStatus status) {
        return new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "parallel-step", null, branchId,
                status, Instant.parse("2026-06-08T10:15:31Z"), Instant.parse("2026-06-08T10:15:31Z"),
                null, null, Map.of(), "item-1");
    }

    private static long meterCount(SimpleMeterRegistry meterRegistry, String name) {
        return meterRegistry.getMeters().stream().filter(meter -> meter.getId().getName().equals(name)).count();
    }
}
