package io.github.gear4jtest.jdbc.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationRecordBufferTargetedCoverageTest {
    @Test
    void constructor_shouldRejectInvalidFlushThresholds() {
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> new OperationRecordBuffer(runId, 2, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("flushThreshold must be > 0");
        assertThatThrownBy(() -> new OperationRecordBuffer(runId, 2, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("flushThreshold must be <= capacity");
    }

    @Test
    void flushScheduling_shouldBeExclusiveUntilCleared() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 2, 2);

        assertThat(buffer.markFlushScheduled()).isTrue();
        assertThat(buffer.markFlushScheduled()).isFalse();

        buffer.clearFlushScheduled();

        assertThat(buffer.markFlushScheduled()).isTrue();
    }

    @Test
    void append_shouldRejectClosedBuffers() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 2, 2);
        buffer.close();

        assertThat(buffer.isClosed()).isTrue();
        assertThatThrownBy(() -> buffer.append(record(), new PersistenceRuntimeCounters()))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("closed run buffer");
    }

    @Test
    void append_shouldRejectWhenCapacityIsFullAndCountRejectedAppend() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1, 1);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();

        assertThat(buffer.append(record(), counters)).isTrue();

        assertThatThrownBy(() -> buffer.append(record(), counters))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("buffer is full");
        assertThat(counters.snapshot(new OperationRecordBufferRegistry(1, 1)).rejectedAppends()).isEqualTo(1L);
    }

    @Test
    void appendAll_shouldAppendBatchUnderOneCapacityCheck() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 3, 2);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        StationLogRecord first = record();
        StationLogRecord second = record();

        assertThat(buffer.appendAll(List.of(first, second), counters)).isTrue();

        assertThat(buffer.pendingCount()).isEqualTo(2);
        assertThat(buffer.drainBatch()).containsExactly(first, second);
    }

    @Test
    void appendAll_shouldRejectWholeBatchWhenCapacityIsInsufficient() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1, 1);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();

        assertThatThrownBy(() -> buffer.appendAll(List.of(record(), record()), counters))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("buffer is full");

        assertThat(buffer.pendingCount()).isZero();
        assertThat(counters.snapshot(new OperationRecordBufferRegistry(1, 1)).rejectedAppends()).isEqualTo(1L);
    }

    @Test
    void drainBatch_shouldReducePendingCountAndRestoreShouldRequeueRecords() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 3, 1);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        StationLogRecord first = record();
        StationLogRecord second = record();
        buffer.append(first, counters);
        buffer.append(second, counters);

        List<StationLogRecord> drained = buffer.drainBatch();

        assertThat(drained).containsExactly(first);
        assertThat(buffer.pendingCount()).isEqualTo(1);
        assertThat(buffer.retainedCount()).isEqualTo(2);

        buffer.restoreDrainedBatch(drained);

        assertThat(buffer.pendingCount()).isEqualTo(2);
        assertThat(buffer.retainedCount()).isEqualTo(2);
    }

    @Test
    void restoreDrainedBatch_shouldIgnoreNullOrEmptyBatches() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1, 1);

        buffer.restoreDrainedBatch(null);
        buffer.restoreDrainedBatch(List.of());

        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    void restoreDrainedBatch_shouldRecordFailureWhenRequeueCannotRestoreEveryRecord() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1, 1);
        StationLogRecord first = record();
        StationLogRecord second = record();

        assertThatThrownBy(() -> buffer.restoreDrainedBatch(List.of(first, second)))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("atomically requeue");

        assertThat(buffer.pendingCount()).isZero();
        assertThat(buffer.currentFailure()).isNotNull();
    }

    private static StationLogRecord record() {
        return new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "station", null, null,
                StationLogStatus.RUNNING, Instant.now(), null, null, null, Map.of(), null);
    }
}
