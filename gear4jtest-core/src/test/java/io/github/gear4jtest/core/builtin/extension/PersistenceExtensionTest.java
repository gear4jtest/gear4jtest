package io.github.gear4jtest.core.builtin.extension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceExtensionTest {
    @Test
    void lifecycleSnapshots_shouldBeEmittedOnceAndFlushedBeforeRunEnd() {
        RecordingRunManager manager = new RecordingRunManager();
        PersistenceExtension extension = new PersistenceExtension(manager);
        PersistenceConfiguration configuration = PersistenceConfiguration.builder()
                .stationLogFlushThreshold(2)
                .build();
        ExecutionContext context = ExecutionContext.builder()
                .services(org.mockito.Mockito.mock(ExecutionServices.class))
                .persistenceConfiguration(configuration)
                .build();
        AssemblyRunTrace run = run();
        StationLogRecord started = record(run.getId(), "station", StationLogStatus.RUNNING);
        StationLogRecord completed = record(run.getId(), "station", StationLogStatus.SUCCEEDED);

        extension.onRunStarted(context, run);
        extension.onStationStarted(context, null, started);
        extension.onStationCompleted(context, null, completed);
        extension.onRunCompleted(context, run);

        assertThat(manager.configuration).isSameAs(configuration);
        assertThat(manager.appended).containsExactly(started, completed);
        assertThat(manager.events).containsExactly("start:2", "append:RUNNING", "append:SUCCEEDED", "flush", "end");
    }

    @Test
    void everyTerminalCallback_shouldDelegateOneSnapshotWithoutExtensionBuffering() {
        RecordingRunManager manager = new RecordingRunManager();
        PersistenceExtension extension = new PersistenceExtension(manager);
        AssemblyRunTrace run = run();
        StationLogRecord record = record(run.getId(), "station", StationLogStatus.SUCCEEDED);

        extension.onStationCompleted(null, null, record);
        extension.onStationSkipped(null, null, record, null);
        extension.onStationCancelled(null, null, record, null, null);
        extension.onStationInterrupted(null, null, record, null, null, null);
        extension.onStationFailedBeforeStart(null, null, record, null);

        assertThat(manager.appended).containsExactly(record, record, record, record, record);
    }

    @Test
    void constructor_shouldRejectNullManager() {
        assertThatThrownBy(() -> new PersistenceExtension(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("manager must not be null");
    }

    @Test
    void missingContext_shouldRemainCompatibleWithManagersImplementingLegacyStartOnly() {
        AtomicInteger starts = new AtomicInteger();
        RunPersistenceManager manager = new RunPersistenceManager() {
            @Override
            public void start(RunTrace execution) {
                starts.incrementAndGet();
            }

            @Override
            public void end(RunTrace finalExecution) {
                // no-op
            }
        };

        new PersistenceExtension(manager).onRunStarted(null, run());

        assertThat(starts).hasValue(1);
    }

    private static AssemblyRunTrace run() {
        AssemblyRunTrace run = new AssemblyRunTrace(UUID.randomUUID(), "pipeline", Map.of());
        run.markStarted();
        return run;
    }

    private static StationLogRecord record(UUID runId, String operationId, StationLogStatus status) {
        Instant now = Instant.now();
        return new StationLogRecord(UUID.randomUUID(), runId, operationId, null, null, status, now,
                status == StationLogStatus.RUNNING ? null : now, null, null, Map.of(), null);
    }

    private static final class RecordingRunManager implements RunPersistenceManager {
        private final List<String> events = new ArrayList<>();
        private final List<StationLogRecord> appended = new ArrayList<>();
        private PersistenceConfiguration configuration;

        @Override
        public void start(RunTrace execution) {
            events.add("start:default");
        }

        @Override
        public void start(RunTrace execution, PersistenceConfiguration configuration) {
            this.configuration = configuration;
            events.add("start:" + configuration.getStationLogFlushThreshold().orElse(-1));
        }

        @Override
        public void append(StationLogRecord stationLogRecord) {
            appended.add(stationLogRecord);
            events.add("append:" + stationLogRecord.status());
        }

        @Override
        public void flush(UUID runId) {
            events.add("flush");
        }

        @Override
        public void end(RunTrace finalExecution) {
            events.add("end");
        }
    }
}
