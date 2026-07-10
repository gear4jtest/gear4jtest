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
    void flushScheduling_shouldBeExclusiveUntilCleared() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 2);

        assertThat(buffer.markFlushScheduled()).isTrue();
        assertThat(buffer.markFlushScheduled()).isFalse();

        buffer.clearFlushScheduled();

        assertThat(buffer.markFlushScheduled()).isTrue();
    }

    @Test
    void append_shouldRejectClosedBuffers() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 2);
        buffer.close();

        assertThat(buffer.isClosed()).isTrue();
        assertThatThrownBy(() -> buffer.append(record(), 10, new PersistenceRuntimeCounters()))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("closed run buffer");
    }

    @Test
    void append_shouldRejectWhenCapacityIsFullAndCountRejectedAppend() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();

        assertThat(buffer.append(record(), 10, counters)).isFalse();

        assertThatThrownBy(() -> buffer.append(record(), 10, counters))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("buffer is full");
        assertThat(counters.snapshot(new OperationRecordBufferRegistry(1)).rejectedAppends()).isEqualTo(1L);
    }

    @Test
    void appendAll_shouldAppendBatchUnderOneCapacityCheck() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 3);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        StationLogRecord first = record();
        StationLogRecord second = record();

        assertThat(buffer.appendAll(List.of(first, second), 2, counters)).isTrue();

        assertThat(buffer.pendingCount()).isEqualTo(2);
        assertThat(buffer.drainBatch(10)).containsExactly(first, second);
    }

    @Test
    void appendAll_shouldRejectWholeBatchWhenCapacityIsInsufficient() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();

        assertThatThrownBy(() -> buffer.appendAll(List.of(record(), record()), 10, counters))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("buffer is full");

        assertThat(buffer.pendingCount()).isZero();
        assertThat(counters.snapshot(new OperationRecordBufferRegistry(1)).rejectedAppends()).isEqualTo(1L);
    }

    @Test
    void drainBatch_shouldReducePendingCountAndRestoreShouldRequeueRecords() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 3);
        PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
        StationLogRecord first = record();
        StationLogRecord second = record();
        buffer.append(first, 10, counters);
        buffer.append(second, 10, counters);

        List<StationLogRecord> drained = buffer.drainBatch(1);

        assertThat(drained).containsExactly(first);
        assertThat(buffer.pendingCount()).isEqualTo(1);
        assertThat(buffer.retainedCount()).isEqualTo(2);

        buffer.restoreDrainedBatch(drained);

        assertThat(buffer.pendingCount()).isEqualTo(2);
        assertThat(buffer.retainedCount()).isEqualTo(2);
    }

    @Test
    void restoreDrainedBatch_shouldIgnoreNullOrEmptyBatches() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1);

        buffer.restoreDrainedBatch(null);
        buffer.restoreDrainedBatch(List.of());

        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    void restoreDrainedBatch_shouldRecordFailureWhenRequeueCannotRestoreEveryRecord() {
        OperationRecordBuffer buffer = new OperationRecordBuffer(UUID.randomUUID(), 1);
        StationLogRecord first = record();
        StationLogRecord second = record();

        buffer.restoreDrainedBatch(List.of(first, second));

        assertThat(buffer.pendingCount()).isEqualTo(1);
        assertThatThrownBy(buffer::assertHealthy)
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("Persistence failed for runId");
    }

    private static StationLogRecord record() {
        return new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "station", null, null,
                StationLogStatus.RUNNING, Instant.now(), null, null, null, Map.of(), null);
    }
}
