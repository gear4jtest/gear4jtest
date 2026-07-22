package io.github.gear4jtest.core.builtin.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.LifecycleFailureMode;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;

/**
 * Persistence extension responsible for run and station durability.
 *
 * <p>
 * If this extension fails, the failure is considered critical.
 * </p>
 *
 * <p>
 * Station start snapshots are persisted immediately so long-running stations
 * are visible as {@code RUNNING} even if the JVM exits before station
 * completion. Terminal station snapshots are buffered per run and flushed with
 * {@link RunPersistenceManager#appendAll(List)} before the run record is ended.
 * This amortizes completion writes while keeping run finalization blocked on
 * the durability of all pending station terminal records.
 * </p>
 */
public class PersistenceExtension implements RunLifecycleExtension, StationLifecycleExtension {
    private static final int DEFAULT_TERMINAL_RECORD_BATCH_SIZE = 128;

    private final RunPersistenceManager manager;
    private final int terminalRecordBatchSize;
    private final ConcurrentMap<UUID, TerminalRecordBuffer> terminalBuffers = new ConcurrentHashMap<>();

    public PersistenceExtension(RunPersistenceManager manager) {
        this(builder(manager));
    }

    private PersistenceExtension(Builder builder) {
        this.manager = Objects.requireNonNull(builder.manager, "manager must not be null");
        this.terminalRecordBatchSize = positive(builder.terminalRecordBatchSize, "terminalRecordBatchSize");
    }

    public static Builder builder(RunPersistenceManager manager) {
        return new Builder(manager);
    }

    public static final class Builder {
        private final RunPersistenceManager manager;
        private int terminalRecordBatchSize = DEFAULT_TERMINAL_RECORD_BATCH_SIZE;

        private Builder(RunPersistenceManager manager) {
            this.manager = Objects.requireNonNull(manager, "manager must not be null");
        }

        /**
         * Maximum number of terminal station snapshots buffered per run before a
         * synchronous {@link RunPersistenceManager#appendAll(List)} is triggered.
         *
         * <p>
         * Use {@code 1} to preserve one terminal append call per station. Larger values
         * amortize persistence calls, but a persistence failure can be observed by a
         * later station completion or by run completion.
         * </p>
         */
        public Builder terminalRecordBatchSize(int terminalRecordBatchSize) {
            this.terminalRecordBatchSize = terminalRecordBatchSize;
            return this;
        }

        public PersistenceExtension build() {
            return new PersistenceExtension(this);
        }
    }

    /**
     * Persistence observes the normalized terminal state after ordinary lifecycle
     * observers had an opportunity to affect the station outcome.
     *
     * <p>
     * If another critical completion observer fails, its {@code FAILED} status must
     * be present in the snapshot appended by this extension. A persistence failure
     * itself cannot, by definition, durably record its own failure.
     * </p>
     */
    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

    @Override
    public LifecycleFailureMode failureMode() {
        return LifecycleFailureMode.CRITICAL;
    }

    @Override
    public void onRunStarted(ExecutionContext ctx, RunTrace run) {
        manager.start(run);
        terminalBuffers.put(run.getId(), new TerminalRecordBuffer());
    }

    @Override
    public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
        flushRun(run.getId());
        manager.end(run);
    }

    @Override
    public void onStationStarted(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot) {
        manager.append(snapshot);
    }

    @Override
    public void onStationCompleted(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot) {
        appendTerminal(snapshot);
    }

    @Override
    public void onStationSkipped(ExecutionContext runCtx,
                                 StationExecutionContext stationCtx,
                                 StationLogRecord snapshot,
                                 StationSkipReason reason) {
        appendTerminal(snapshot);
    }

    @Override
    public void onStationCancelled(ExecutionContext runCtx,
                                   StationExecutionContext stationCtx,
                                   StationLogRecord snapshot,
                                   StationCancellationReason reason,
                                   Exception error) {
        appendTerminal(snapshot);
    }

    @Override
    public void onStationInterrupted(ExecutionContext runCtx,
                                     StationExecutionContext stationCtx,
                                     StationLogRecord snapshot,
                                     StationInterruptionReason reason,
                                     String interruptingOperationId,
                                     Exception error) {
        appendTerminal(snapshot);
    }

    @Override
    public void onStationFailedBeforeStart(ExecutionContext runCtx,
                                           StationExecutionContext stationCtx,
                                           StationLogRecord snapshot,
                                           Exception error) {
        appendTerminal(snapshot);
    }

    private void appendTerminal(StationLogRecord snapshot) {
        List<StationLogRecord> batch = bufferFor(snapshot.assemblyLineExecutionId()).add(snapshot,
                                                                                         terminalRecordBatchSize);
        flush(batch);
    }

    private TerminalRecordBuffer bufferFor(UUID runId) {
        return terminalBuffers.computeIfAbsent(runId, ignored -> new TerminalRecordBuffer());
    }

    private void flushRun(UUID runId) {
        TerminalRecordBuffer buffer = terminalBuffers.remove(runId);
        if (buffer != null) {
            flush(buffer.drain());
        }
    }

    private void flush(List<StationLogRecord> batch) {
        if (!batch.isEmpty()) {
            manager.appendAll(batch);
        }
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static final class TerminalRecordBuffer {
        private final List<StationLogRecord> records = new ArrayList<>();

        synchronized List<StationLogRecord> add(StationLogRecord record, int batchSize) {
            records.add(record);
            if (records.size() < batchSize) {
                return List.of();
            }
            return drain();
        }

        synchronized List<StationLogRecord> drain() {
            if (records.isEmpty()) {
                return List.of();
            }
            List<StationLogRecord> drained = List.copyOf(records);
            records.clear();
            return drained;
        }
    }
}
