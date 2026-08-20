package io.github.gear4jtest.jdbc.execution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/**
 * Persists one drained batch and conservatively isolates proven record
 * failures.
 */
final class PersistenceBatchProcessor {
    private final PersistenceRuntimeCounters counters;
    private final JdbcPersistenceFailureClassifier classifier;
    private final RejectedPersistenceRecordHandler rejectedRecordHandler;

    PersistenceBatchProcessor(PersistenceRuntimeCounters counters,
                              RejectedPersistenceRecordHandler rejectedRecordHandler) {
        this.counters = counters;
        this.classifier = new JdbcPersistenceFailureClassifier();
        this.rejectedRecordHandler = rejectedRecordHandler;
    }

    void persist(OperationRecordBuffer buffer, List<StationLogRecord> batch, BatchWriter writer) {
        persist(buffer, batch, writer, this::invokeRejectedRecordHandler);
    }

    void persist(OperationRecordBuffer buffer,
                 List<StationLogRecord> batch,
                 BatchWriter writer,
                 RejectedRecordWriter rejectedRecordWriter) {
        List<IndexedRecord> indexed = new ArrayList<>(batch.size());
        for (int index = 0; index < batch.size(); index++) {
            indexed.add(new IndexedRecord(index, batch.get(index)));
        }
        List<IndexedRecord> unresolved = new ArrayList<>();
        Exception failure = persistSubset(buffer, indexed, writer, rejectedRecordWriter, unresolved);
        if (failure == null) {
            buffer.clearFailure();
            return;
        }
        unresolved.sort(Comparator.comparingInt(IndexedRecord::index));
        try {
            buffer.restoreDrainedBatch(unresolved.stream().map(IndexedRecord::record).toList());
        } catch (RuntimeException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
        buffer.recordFailure(failure);
        throw propagate(failure);
    }

    private Exception persistSubset(OperationRecordBuffer buffer,
                                    List<IndexedRecord> records,
                                    BatchWriter writer,
                                    RejectedRecordWriter rejectedRecordWriter,
                                    List<IndexedRecord> unresolved) {
        try {
            writer.write(records.stream().map(IndexedRecord::record).toList());
            buffer.acknowledgeDrainedBatch(records.stream().map(IndexedRecord::record).toList());
            counters.recordSuccessfulFlushProgress();
            return null;
        } catch (Exception failure) {
            if (classifier.classify(failure) != PersistenceFailureDisposition.RECORD_REJECTED) {
                unresolved.addAll(records);
                return failure;
            }
            if (records.size() == 1) {
                return handleRejectedRecord(buffer, records.get(0), failure, rejectedRecordWriter, unresolved);
            }
            int midpoint = records.size() / 2;
            List<IndexedRecord> first = records.subList(0, midpoint);
            List<IndexedRecord> second = records.subList(midpoint, records.size());
            Exception firstFailure = persistSubset(buffer, first, writer, rejectedRecordWriter, unresolved);
            if (firstFailure != null) {
                unresolved.addAll(second);
                return firstFailure;
            }
            return persistSubset(buffer, second, writer, rejectedRecordWriter, unresolved);
        }
    }

    private Exception handleRejectedRecord(OperationRecordBuffer buffer,
                                           IndexedRecord indexedRecord,
                                           Exception persistenceFailure,
                                           RejectedRecordWriter rejectedRecordWriter,
                                           List<IndexedRecord> unresolved) {
        try {
            rejectedRecordWriter.write(indexedRecord.record(), classifier.rejectionContext(persistenceFailure));
            buffer.quarantineDrainedRecord(indexedRecord.record());
            counters.recordQuarantinedStationLog();
            counters.recordSuccessfulFlushProgress();
            return null;
        } catch (Exception handlerFailure) {
            handlerFailure.addSuppressed(persistenceFailure);
            unresolved.add(indexedRecord);
            return handlerFailure;
        }
    }

    void invokeRejectedRecordHandler(StationLogRecord record, RejectedPersistenceRecordContext context) {
        rejectedRecordHandler.handle(record, context);
    }

    private static RuntimeException propagate(Exception failure) {
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new ExecutionPersistenceException("JDBC persistence batch failed", failure);
    }

    @FunctionalInterface
    interface BatchWriter {
        void write(List<StationLogRecord> records);
    }

    @FunctionalInterface
    interface RejectedRecordWriter {
        void write(StationLogRecord record, RejectedPersistenceRecordContext context);
    }

    private record IndexedRecord(int index, StationLogRecord record) {}
}
