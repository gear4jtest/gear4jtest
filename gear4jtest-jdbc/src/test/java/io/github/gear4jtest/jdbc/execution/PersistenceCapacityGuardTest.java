package io.github.gear4jtest.jdbc.execution;

import java.util.List;
import java.util.UUID;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PersistenceCapacityGuardTest {
    @Test
    void rejectedGlobalAppend_shouldNotLeaveAnEmptyRunRegistered() {
        PersistenceCapacityGuard guard = new PersistenceCapacityGuard(10, 1);
        OperationRecordBufferRegistry buffers = new OperationRecordBufferRegistry(10, 5, guard);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();

        buffers.appendAll(UUID.randomUUID(), List.of(mock(StationLogRecord.class)), counters);

        assertThatThrownBy(() -> buffers.appendAll(UUID.randomUUID(),
                                                   List.of(mock(StationLogRecord.class)), counters))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("maxBufferedStationLogs=1");
        assertThat(buffers.activeRunCount()).isEqualTo(1);
        assertThat(buffers.bufferedStationLogCount()).isEqualTo(1);
        assertThat(counters.snapshot(buffers).rejectedAppends()).isEqualTo(1L);
    }

    @Test
    void activeRunPermit_shouldBeReleasedOnlyAfterTheBufferIsRemovable() {
        PersistenceCapacityGuard guard = new PersistenceCapacityGuard(1, 10);
        OperationRecordBufferRegistry buffers = new OperationRecordBufferRegistry(10, 5, guard);
        UUID firstRun = UUID.randomUUID();
        UUID secondRun = UUID.randomUUID();

        buffers.createFresh(firstRun, 5);
        assertThatThrownBy(() -> buffers.createFresh(secondRun, 5))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("maxActiveRuns=1");

        assertThat(buffers.remove(firstRun)).isTrue();
        assertThat(buffers.createFresh(secondRun, 5)).isNotNull();
        assertThat(buffers.activeRunCount()).isEqualTo(1);
    }

    @Test
    void stationLogPermit_shouldRemainHeldWhileAJdbcBatchIsInFlight() {
        PersistenceCapacityGuard guard = new PersistenceCapacityGuard(2, 1);
        OperationRecordBuffer first = new OperationRecordBuffer(UUID.randomUUID(), 1, 1, guard);
        OperationRecordBuffer second = new OperationRecordBuffer(UUID.randomUUID(), 1, 1, guard);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        StationLogRecord firstRecord = mock(StationLogRecord.class);

        first.append(firstRecord, counters);
        List<StationLogRecord> inFlight = first.drainBatch();

        assertThat(guard.bufferedStationLogs()).isEqualTo(1);
        assertThatThrownBy(() -> second.append(mock(StationLogRecord.class), counters))
                .isInstanceOf(ExecutionPersistenceException.class);

        first.acknowledgeDrainedBatch(inFlight);
        assertThat(second.append(mock(StationLogRecord.class), counters)).isTrue();
    }
}
