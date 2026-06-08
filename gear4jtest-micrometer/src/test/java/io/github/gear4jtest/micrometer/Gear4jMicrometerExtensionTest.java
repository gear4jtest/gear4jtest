package io.github.gear4jtest.micrometer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        assertThat(meterRegistry.counter("gear4j.runs.started", "pipeline.id", "checkout").count())
                .as("started runs counter")
                .isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.runs.completed", "pipeline.id", "checkout", "status",
                                         "SUCCEEDED")
                .count())
                .as("completed runs counter")
                .isEqualTo(1.0d);
        double durationMillis = meterRegistry.timer("gear4j.runs.duration", "pipeline.id", "checkout", "status",
                                                    "SUCCEEDED")
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
        assertThat(meterRegistry.counter("gear4j.stations.started", "operation.id", "normalize", "branch.id",
                                         "branch-a")
                .count())
                .as("started stations counter")
                .isEqualTo(1.0d);
        assertThat(meterRegistry.counter("gear4j.stations.completed", "operation.id", "normalize", "branch.id",
                                         "branch-a", "status", "SUCCEEDED")
                .count())
                .as("completed stations counter")
                .isEqualTo(1.0d);
        double durationMillis = meterRegistry.timer("gear4j.stations.duration", "operation.id", "normalize",
                                                    "branch.id", "branch-a", "status", "SUCCEEDED")
                .totalTime(TimeUnit.MILLISECONDS);
        assertThat(durationMillis)
                .as("completed station duration")
                .isEqualTo(1000.0d);
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
}
