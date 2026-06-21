package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates asynchronous and blocking JDBC flushes for run log buffers. */
final class PersistenceFlushCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceFlushCoordinator.class);

    private final DatabaseAssemblyRunRepository repository;
    private final PersistenceRuntimeConfiguration configuration;
    private final OperationRecordBufferRegistry buffers;
    private final ExecutorService flushExecutor;
    private final ScheduledExecutorService maintenanceExecutor;
    private final boolean ownsFlushExecutor;
    private final boolean ownsMaintenanceExecutor;
    private final PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
    private final ScheduledFuture<?> periodicFlushTask;
    private final AtomicBoolean shutdown = new AtomicBoolean();

    PersistenceFlushCoordinator(DatabaseAssemblyRunRepository repository,
                                PersistenceRuntimeConfiguration configuration,
                                OperationRecordBufferRegistry buffers,
                                ExecutorService flushExecutor,
                                ScheduledExecutorService maintenanceExecutor,
                                boolean ownsFlushExecutor,
                                boolean ownsMaintenanceExecutor) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.buffers = Objects.requireNonNull(buffers, "buffers must not be null");
        this.flushExecutor = Objects.requireNonNull(flushExecutor, "flushExecutor must not be null");
        this.maintenanceExecutor = Objects.requireNonNull(maintenanceExecutor,
                                                          "maintenanceExecutor must not be null");
        this.ownsFlushExecutor = ownsFlushExecutor;
        this.ownsMaintenanceExecutor = ownsMaintenanceExecutor;
        long intervalNanos = configuration.flushInterval().toNanos();
        this.periodicFlushTask = this.maintenanceExecutor.scheduleWithFixedDelay(this::flushPendingBuffersSafely,
                                                                                 intervalNanos, intervalNanos,
                                                                                 TimeUnit.NANOSECONDS);
    }

    static ExecutorService createFlushExecutor(PersistenceRuntimeConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        int flushThreadCount = configuration.flushThreadCount();
        return new ThreadPoolExecutor(flushThreadCount, flushThreadCount, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(configuration.maxScheduledFlushTasks()),
                PersistenceThreadFactories.flushWorker(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    PersistenceRuntimeCounters counters() {
        return counters;
    }

    PersistenceRuntimeStats snapshotStats() {
        return counters.snapshot(buffers);
    }

    void ensureOpen() {
        if (shutdown.get()) {
            throw new ExecutionPersistenceException("DatabaseExecutionManager is already shut down");
        }
    }

    void scheduleAsyncFlush(OperationRecordBuffer buffer, boolean drainCompletely) {
        if (!buffer.markFlushScheduled()) {
            return;
        }
        if (shutdown.get()) {
            buffer.clearFlushScheduled();
            return;
        }
        counters.recordScheduledFlush();
        try {
            flushExecutor.execute(() -> {
                try {
                    flushBufferBlocking(buffer, drainCompletely);
                    counters.recordCompletedFlush();
                } catch (Exception e) {
                    counters.recordFailedFlush();
                    if (drainCompletely || buffer.isClosed()) {
                        buffer.recordFailure(e);
                    }
                    LOGGER.error("Asynchronous station log flush failed. runId={}", buffer.runId(), e);
                }
            });
        } catch (RejectedExecutionException e) {
            buffer.clearFlushScheduled();
            counters.recordFailedFlush();
            buffer.recordFailure(e);
            throw new ExecutionPersistenceException("Station log flush executor rejected a flush. runId="
                    + buffer.runId(), e);
        }
    }

    void flushBufferBlocking(OperationRecordBuffer buffer, boolean drainCompletely) {
        buffer.lockFlush();
        try {
            buffer.assertHealthy();
            do {
                List<StationLogRecord> batch = buffer.drainBatch(configuration.batchSize());
                if (batch.isEmpty()) {
                    return;
                }
                try {
                    repository.saveOperationRecordsBatch(batch);
                } catch (Exception e) {
                    buffer.restoreDrainedBatch(batch);
                    if (drainCompletely || buffer.isClosed()) {
                        buffer.recordFailure(e);
                    }
                    throw e;
                }
            } while (drainCompletely);
        } finally {
            buffer.clearFlushScheduled();
            buffer.unlockFlush();
        }
        if (!shutdown.get() && buffer.pendingCount() >= configuration.batchSize()) {
            scheduleAsyncFlush(buffer, false);
        }
    }

    void shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        periodicFlushTask.cancel(false);
        if (ownsMaintenanceExecutor) {
            maintenanceExecutor.shutdownNow();
        }
        if (ownsFlushExecutor) {
            flushExecutor.shutdown();
        }
        for (OperationRecordBuffer buffer : buffers.activeBuffers()) {
            try {
                buffer.close();
                flushBufferBlocking(buffer, true);
                buffers.remove(buffer.runId());
            } catch (Exception e) {
                counters.recordFailedFlush();
                LOGGER.error("Failed to flush buffered station logs during shutdown. runId={}", buffer.runId(), e);
            }
        }
        if (ownsFlushExecutor) {
            awaitFlushExecutorTermination(timeout);
        }
    }

    private void flushPendingBuffersSafely() {
        if (shutdown.get()) {
            return;
        }
        for (OperationRecordBuffer buffer : buffers.activeBuffers()) {
            if (buffer.pendingCount() > 0 && !buffer.isClosed()) {
                scheduleAsyncFlush(buffer, false);
            }
        }
    }

    private void awaitFlushExecutorTermination(Duration timeout) {
        try {
            if (!flushExecutor.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                List<Runnable> droppedTasks = flushExecutor.shutdownNow();
                throw new ExecutionPersistenceException("Timed out while waiting for Gear4J persistence flush "
                        + "executor to terminate. droppedTasks=" + droppedTasks.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            List<Runnable> droppedTasks = flushExecutor.shutdownNow();
            throw new ExecutionPersistenceException("Interrupted while waiting for Gear4J persistence flush "
                    + "executor to terminate. droppedTasks=" + droppedTasks.size(), e);
        }
    }
}
