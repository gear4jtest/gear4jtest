package io.github.gear4jtest.core.builtin.extension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.AssemblyRunManager;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceExtensionTest {
    @Test
    void stationStarted_shouldPersistImmediatelyWhileTerminalSnapshotsAreBatched() {
        RecordingRunManager manager = new RecordingRunManager();
        PersistenceExtension extension = PersistenceExtension.builder(manager)
                .terminalRecordBatchSize(2)
                .build();
        AssemblyRunTrace run = run();

        extension.onRunStarted(null, run);
        extension.onStationStarted(null, null, record(run.getId(), "first", StationLogStatus.RUNNING));
        extension.onStationCompleted(null, null, record(run.getId(), "first", StationLogStatus.SUCCEEDED));

        assertThat(manager.appended).extracting(StationLogRecord::operationId).containsExactly("first");
        assertThat(manager.appendedBatches).isEmpty();

        extension.onStationCompleted(null, null, record(run.getId(), "second", StationLogStatus.SUCCEEDED));

        assertThat(manager.appendedBatches).hasSize(1);
        assertThat(manager.appendedBatches.get(0)).extracting(StationLogRecord::operationId)
                .containsExactly("first", "second");
    }

    @Test
    void onRunCompleted_shouldFlushRemainingTerminalSnapshotsBeforeEndingRun() {
        RecordingRunManager manager = new RecordingRunManager();
        PersistenceExtension extension = PersistenceExtension.builder(manager)
                .terminalRecordBatchSize(10)
                .build();
        AssemblyRunTrace run = run();

        extension.onRunStarted(null, run);
        extension.onStationCompleted(null, null, record(run.getId(), "only", StationLogStatus.SUCCEEDED));
        extension.onRunCompleted(null, run);

        assertThat(manager.events).containsExactly("start", "appendAll:1", "end");
        assertThat(manager.appendedBatches.get(0)).extracting(StationLogRecord::operationId)
                .containsExactly("only");
    }

    @Test
    void terminalRecordBatchSize_shouldRejectNonPositiveValues() {
        RecordingRunManager manager = new RecordingRunManager();

        assertThatThrownBy(() -> PersistenceExtension.builder(manager).terminalRecordBatchSize(0).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terminalRecordBatchSize must be > 0");
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

    private static final class RecordingRunManager implements AssemblyRunManager {
        private final List<String> events = new ArrayList<>();
        private final List<StationLogRecord> appended = new ArrayList<>();
        private final List<List<StationLogRecord>> appendedBatches = new ArrayList<>();

        @Override
        public void start(AssemblyRunTrace execution) {
            events.add("start");
        }

        @Override
        public void append(StationLogRecord stationLogRecord) {
            appended.add(stationLogRecord);
            events.add("append");
        }

        @Override
        public void appendAll(List<StationLogRecord> records) {
            appendedBatches.add(List.copyOf(records));
            events.add("appendAll:" + records.size());
        }

        @Override
        public void end(AssemblyRunTrace finalExecution) {
            events.add("end");
        }
    }
}
