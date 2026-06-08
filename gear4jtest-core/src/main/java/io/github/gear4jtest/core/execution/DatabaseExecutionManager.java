package io.github.gear4jtest.core.execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.security.SensitiveDataRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBC-backed run manager with bounded station-log buffering and asynchronous
 * batched flushes.
 */
public class DatabaseExecutionManager implements AssemblyRunManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExecutionManager.class);
    private final DatabaseAssemblyRunRepository repository;
    private final PersistenceRuntimeConfiguration configuration;
    private final Map<UUID, RunBuffer> buffers = new ConcurrentHashMap<>();
    private final ExecutorService flushExecutor;
    private final ScheduledExecutorService maintenanceExecutor;
    private final boolean ownsFlushExecutor;
    private final boolean ownsMaintenanceExecutor;
    private final SensitiveDataRedactor redactor;
    private final ScheduledFuture<?> periodicFlushTask;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicLong scheduledFlushes = new AtomicLong();
    private final AtomicLong completedFlushes = new AtomicLong();
    private final AtomicLong failedFlushes = new AtomicLong();
    private final AtomicLong rejectedAppends = new AtomicLong();

    public DatabaseExecutionManager(DataSource dataSource, Gear4jDatabaseDialect databaseDialect) {
        this(dataSource, databaseDialect, PersistenceRuntimeConfiguration.defaults(), true,
                SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DataSource dataSource,
                                    Gear4jDatabaseDialect databaseDialect,
                                    int flushThreshold,
                                    boolean autoCreateTables) {
        this(dataSource, databaseDialect,
                PersistenceRuntimeConfiguration.builder().batchSize(flushThreshold)
                        .maxPendingLogsPerRun(Math.max(flushThreshold, 10_000)).build(),
                autoCreateTables, SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DataSource dataSource,
                                    Gear4jDatabaseDialect databaseDialect,
                                    PersistenceRuntimeConfiguration configuration,
                                    boolean autoCreateTables) {
        this(dataSource, databaseDialect, configuration, autoCreateTables, SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DataSource dataSource,
                                    Gear4jDatabaseDialect databaseDialect,
                                    PersistenceRuntimeConfiguration configuration,
                                    boolean autoCreateTables,
                                    SensitiveDataRedactor redactor) {
        this(new DatabaseAssemblyRunRepository(Objects.requireNonNull(dataSource, "dataSource must not be null"),
                Objects.requireNonNull(databaseDialect, "databaseDialect must not be null")), configuration,
                autoCreateTables, Executors.newSingleThreadExecutor(new Gear4jFlushThreadFactory()),
                Executors.newSingleThreadScheduledExecutor(new Gear4jMaintenanceThreadFactory()), true, true,
                redactor);
    }

    /**
     * Compatibility constructor for caller-managed flush executors. The supplied
     * executor is never shut down by this manager.
     */
    public DatabaseExecutionManager(DatabaseAssemblyRunRepository repository,
                                    int flushThreshold,
                                    boolean autoCreateTables,
                                    ExecutorService flushExecutor) {
        this(repository,
                PersistenceRuntimeConfiguration.builder().batchSize(flushThreshold)
                        .maxPendingLogsPerRun(Math.max(flushThreshold, 10_000)).build(),
                autoCreateTables, flushExecutor,
                Executors.newSingleThreadScheduledExecutor(new Gear4jMaintenanceThreadFactory()), false, true,
                SensitiveDataRedactor.none());
    }

    public DatabaseExecutionManager(DatabaseAssemblyRunRepository repository,
                                    PersistenceRuntimeConfiguration configuration,
                                    boolean autoCreateTables,
                                    ExecutorService flushExecutor,
                                    ScheduledExecutorService maintenanceExecutor) {
        this(repository, configuration, autoCreateTables, flushExecutor, maintenanceExecutor, false, false,
                SensitiveDataRedactor.none());
    }

    private DatabaseExecutionManager(DatabaseAssemblyRunRepository repository,
                                     PersistenceRuntimeConfiguration configuration,
                                     boolean autoCreateTables,
                                     ExecutorService flushExecutor,
                                     ScheduledExecutorService maintenanceExecutor,
                                     boolean ownsFlushExecutor,
                                     boolean ownsMaintenanceExecutor,
                                     SensitiveDataRedactor redactor) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.flushExecutor = Objects.requireNonNull(flushExecutor, "flushExecutor must not be null");
        this.maintenanceExecutor = Objects.requireNonNull(maintenanceExecutor, "maintenanceExecutor must not be null");
        this.ownsFlushExecutor = ownsFlushExecutor;
        this.ownsMaintenanceExecutor = ownsMaintenanceExecutor;
        this.redactor = redactor != null ? redactor : SensitiveDataRedactor.none();
        if (SensitiveDataRedactor.isNone(this.redactor)) {
            LOGGER.warn("[Gear4J] JDBC persistence is enabled with no SensitiveDataRedactor. "
                    + "Pipeline payloads, contexts and results will be persisted as-is.");
        }
        if (autoCreateTables) {
            this.repository.initialize();
        }
        long intervalNanos = configuration.flushInterval().toNanos();
        this.periodicFlushTask = this.maintenanceExecutor.scheduleWithFixedDelay(this::flushPendingBuffersSafely,
                                                                                 intervalNanos, intervalNanos,
                                                                                 TimeUnit.NANOSECONDS);
    }

    @Override
    public void start(AssemblyRunTrace execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        ensureOpen();
        repository.save(AssemblyRunRecord.from(execution, redactor));
        buffers.put(execution.getId(), new RunBuffer(execution.getId(), configuration.maxPendingLogsPerRun()));
    }

    @Override
    public void append(StationLogRecord record) {
        if (record == null) {
            return;
        }
        ensureOpen();
        record = record.redactedWith(redactor);
        UUID runId = record.pipelineExecutionId();
        RunBuffer buffer = buffers.computeIfAbsent(runId,
                                                   id -> new RunBuffer(id, configuration.maxPendingLogsPerRun()));
        assertHealthy(buffer);
        boolean shouldScheduleFlush;
        buffer.flushLock.lock();
        try {
            assertHealthy(buffer);
            if (buffer.closed.get()) {
                throw new ExecutionPersistenceException(
                        "Cannot append station log to a closed run buffer. runId=" + runId
                                + ", stationLogId=" + record.id());
            }
            if (!buffer.queue.offer(record)) {
                rejectedAppends.incrementAndGet();
                throw new ExecutionPersistenceException("Station log persistence buffer is full. runId=" + runId
                        + ", maxPendingLogsPerRun=" + configuration.maxPendingLogsPerRun());
            }
            shouldScheduleFlush = buffer.pendingCount.incrementAndGet() >= configuration.batchSize();
        } finally {
            buffer.flushLock.unlock();
        }
        if (shouldScheduleFlush) {
            scheduleAsyncFlush(buffer, false);
        }
    }

    @Override
    public void appendAll(List<StationLogRecord> records) {
        if (records != null) {
            records.forEach(this::append);
        }
    }

    @Override
    public void flush(UUID pipelineId) {
        if (pipelineId == null) {
            return;
        }
        RunBuffer buffer = buffers.get(pipelineId);
        if (buffer != null) {
            assertHealthy(buffer);
            flushBufferBlocking(buffer, false);
            assertHealthy(buffer);
        }
    }

    @Override
    public void end(AssemblyRunTrace finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");
        UUID runId = finalExecution.getId();
        RunBuffer buffer = buffers.computeIfAbsent(runId,
                                                   id -> new RunBuffer(id, configuration.maxPendingLogsPerRun()));
        buffer.closed.set(true);
        try {
            assertHealthy(buffer);
            flushBufferBlocking(buffer, true);
            assertHealthy(buffer);
            repository.update(AssemblyRunRecord.from(finalExecution, redactor));
        } finally {
            buffers.remove(runId);
        }
    }

    public PersistenceRuntimeStats snapshotStats() {
        int buffered = buffers.values().stream().mapToInt(buffer -> buffer.pendingCount.get()).sum();
        return new PersistenceRuntimeStats(buffers.size(), buffered, scheduledFlushes.get(), completedFlushes.get(),
                failedFlushes.get(), rejectedAppends.get());
    }

    @Override
    public void shutdown() {
        shutdown(configuration.shutdownTimeout());
    }

    public void shutdown(Duration timeout) {
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
        for (RunBuffer buffer : buffers.values()) {
            try {
                buffer.closed.set(true);
                flushBufferBlocking(buffer, true);
            } catch (Exception e) {
                LOGGER.error("Failed to flush buffered station logs during shutdown. runId={}", buffer.runId, e);
            }
        }
        buffers.clear();
        if (ownsFlushExecutor) {
            flushExecutor.shutdown();
            awaitFlushExecutorTermination(timeout);
        }
    }

    private void ensureOpen() {
        if (shutdown.get()) {
            throw new ExecutionPersistenceException("DatabaseExecutionManager is already shut down");
        }
    }

    private void flushPendingBuffersSafely() {
        if (shutdown.get()) {
            return;
        }
        for (RunBuffer buffer : buffers.values()) {
            if (buffer.pendingCount.get() > 0 && !buffer.closed.get()) {
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

    private void scheduleAsyncFlush(RunBuffer buffer, boolean drainCompletely) {
        if (!buffer.flushScheduled.compareAndSet(false, true)) {
            return;
        }
        scheduledFlushes.incrementAndGet();
        try {
            flushExecutor.execute(() -> {
                try {
                    flushBufferBlocking(buffer, drainCompletely);
                    completedFlushes.incrementAndGet();
                } catch (Exception e) {
                    failedFlushes.incrementAndGet();
                    if (drainCompletely || buffer.closed.get()) {
                        recordFailure(buffer, e);
                    }
                    LOGGER.error("Asynchronous station log flush failed. runId={}", buffer.runId, e);
                }
            });
        } catch (RejectedExecutionException e) {
            buffer.flushScheduled.set(false);
            failedFlushes.incrementAndGet();
            recordFailure(buffer, e);
            throw new ExecutionPersistenceException("Station log flush executor rejected a flush. runId="
                    + buffer.runId, e);
        }
    }

    private void flushBufferBlocking(RunBuffer buffer, boolean drainCompletely) {
        buffer.flushLock.lock();
        try {
            assertHealthy(buffer);
            do {
                List<StationLogRecord> batch = drainBatch(buffer);
                if (batch.isEmpty()) {
                    return;
                }
                try {
                    repository.saveOperationRecordsBatch(batch);
                } catch (Exception e) {
                    restoreDrainedBatch(buffer, batch);
                    if (drainCompletely || buffer.closed.get()) {
                        recordFailure(buffer, e);
                    }
                    throw e;
                }
            } while (drainCompletely);
        } finally {
            buffer.flushScheduled.set(false);
            buffer.flushLock.unlock();
        }
        if (!drainCompletely && buffer.pendingCount.get() >= configuration.batchSize()) {
            scheduleAsyncFlush(buffer, false);
        }
    }

    private List<StationLogRecord> drainBatch(RunBuffer buffer) {
        List<StationLogRecord> batch = new ArrayList<>(configuration.batchSize());
        buffer.queue.drainTo(batch, configuration.batchSize());
        if (!batch.isEmpty()) {
            buffer.pendingCount.addAndGet(-batch.size());
        }
        return batch;
    }

    private void restoreDrainedBatch(RunBuffer buffer, List<StationLogRecord> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (StationLogRecord record : batch) {
            if (!buffer.queue.offer(record)) {
                recordFailure(buffer, new ExecutionPersistenceException(
                        "Could not requeue drained station log after failed persistence flush. runId=" + buffer.runId
                                + ", stationLogId=" + record.id()));
                break;
            }
            buffer.pendingCount.incrementAndGet();
        }
    }

    private void recordFailure(RunBuffer buffer, Exception failure) {
        buffer.firstFailure.compareAndSet(null,
                                          new ExecutionPersistenceException(
                                                  "Persistence failed for runId=" + buffer.runId, failure));
    }

    private void assertHealthy(RunBuffer buffer) {
        ExecutionPersistenceException failure = buffer.firstFailure.get();
        if (failure != null) {
            throw failure;
        }
    }

    private static final class RunBuffer {
        private final UUID runId;
        private final ArrayBlockingQueue<StationLogRecord> queue;
        private final AtomicInteger pendingCount = new AtomicInteger();
        private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final java.util.concurrent.locks.ReentrantLock flushLock = new java.util.concurrent.locks.ReentrantLock();
        private final AtomicReference<ExecutionPersistenceException> firstFailure = new AtomicReference<>();

        private RunBuffer(UUID runId, int capacity) {
            this.runId = runId;
            this.queue = new ArrayBlockingQueue<>(capacity);
        }
    }

    private static final class Gear4jFlushThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "gear4j-db-flush-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class Gear4jMaintenanceThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "gear4j-db-flush-timer-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
