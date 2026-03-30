package io.github.gear4jtest.core.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.AssemblyRun;
import io.github.gear4jtest.core.persistence.DatabaseAssemblyRunRepository;
import io.github.gear4jtest.core.persistence.StationLogSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseExecutionManager implements AssemblyRunManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExecutionManager.class);

    private final DatabaseAssemblyRunRepository repository;
    private final Map<UUID, RunBuffer> buffers = new ConcurrentHashMap<>();
    private final ExecutorService flushExecutor;
    private final int flushThreshold;

    public DatabaseExecutionManager(DataSource dataSource) {
        this(dataSource, 500, true);
    }

    public DatabaseExecutionManager(DataSource dataSource, int flushThreshold, boolean autoCreateTables) {
        this(
                new DatabaseAssemblyRunRepository(Objects.requireNonNull(dataSource, "dataSource must not be null")),
                flushThreshold,
                autoCreateTables,
                Executors.newSingleThreadExecutor(new Gear4jFlushThreadFactory()));
    }

    public DatabaseExecutionManager(
            DatabaseAssemblyRunRepository repository,
            int flushThreshold,
            boolean autoCreateTables,
            ExecutorService flushExecutor) {

        if (flushThreshold <= 0) {
            throw new IllegalArgumentException("flushThreshold must be > 0");
        }

        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.flushThreshold = flushThreshold;
        this.flushExecutor = Objects.requireNonNull(flushExecutor, "flushExecutor must not be null");

        if (autoCreateTables) {
            this.repository.initialize();
        }
    }

    @Override
    public void start(AssemblyRun execution) {
        Objects.requireNonNull(execution, "execution must not be null");

        repository.save(execution);
        buffers.put(execution.getId(), new RunBuffer(execution.getId()));
    }

    @Override
    public void append(StationLogSnapshot record) {
        if (record == null) {
            return;
        }

        UUID runId = record.pipelineExecutionId();
        RunBuffer buffer = buffers.computeIfAbsent(runId, RunBuffer::new);

        assertHealthy(buffer);

        if (buffer.closed.get()) {
            throw new ExecutionPersistenceException(
                    "Cannot append station log to a closed run buffer. runId=" + runId + ", stationLogId=" + record.id());
        }

        buffer.queue.add(record);
        int size = buffer.pendingCount.incrementAndGet();

        if (size >= flushThreshold) {
            scheduleAsyncFlush(buffer, false);
        }
    }

    @Override
    public void appendAll(List<StationLogSnapshot> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        for (StationLogSnapshot record : records) {
            append(record);
        }
    }

    @Override
    public void flush(UUID pipelineId) {
        if (pipelineId == null) {
            return;
        }

        RunBuffer buffer = buffers.get(pipelineId);
        if (buffer == null) {
            return;
        }

        assertHealthy(buffer);
        flushBufferBlocking(buffer, false);
        assertHealthy(buffer);
    }

    @Override
    public void end(AssemblyRun finalExecution) {
        Objects.requireNonNull(finalExecution, "finalExecution must not be null");

        UUID runId = finalExecution.getId();
        RunBuffer buffer = buffers.computeIfAbsent(runId, RunBuffer::new);

        buffer.closed.set(true);

        try {
            assertHealthy(buffer);
            flushBufferBlocking(buffer, true);
            assertHealthy(buffer);
            repository.update(finalExecution);
        } finally {
            buffers.remove(runId);
        }
    }

    @Override
    public void shutdown() {
        for (RunBuffer buffer : buffers.values()) {
            try {
                buffer.closed.set(true);
                flushBufferBlocking(buffer, true);
            } catch (Exception e) {
                LOGGER.error("Failed to flush buffered station logs during shutdown. runId={}", buffer.runId, e);
            }
        }

        buffers.clear();
        flushExecutor.shutdown();
    }

    private void scheduleAsyncFlush(RunBuffer buffer, boolean drainCompletely) {
        if (!buffer.flushScheduled.compareAndSet(false, true)) {
            return;
        }

        flushExecutor.execute(() -> {
            try {
                flushBufferBlocking(buffer, drainCompletely);
            } catch (Exception e) {
                recordFailure(buffer, e);
                LOGGER.error("Asynchronous station log flush failed. runId={}", buffer.runId, e);
            }
        });
    }

    private void flushBufferBlocking(RunBuffer buffer, boolean drainCompletely) {
        assertHealthy(buffer);

        buffer.flushLock.lock();
        try {
            do {
                List<StationLogSnapshot> batch = drainBatch(buffer);
                if (batch.isEmpty()) {
                    return;
                }

                repository.saveOperationSnapshotsBatch(batch);
            } while (drainCompletely);
        } catch (Exception e) {
            recordFailure(buffer, e);
            throw e;
        } finally {
            buffer.flushScheduled.set(false);
            buffer.flushLock.unlock();
        }

        if (!drainCompletely && buffer.pendingCount.get() >= flushThreshold) {
            scheduleAsyncFlush(buffer, false);
        }
    }

    private List<StationLogSnapshot> drainBatch(RunBuffer buffer) {
        List<StationLogSnapshot> batch = new ArrayList<>(flushThreshold);

        for (int i = 0; i < flushThreshold; i++) {
            StationLogSnapshot record = buffer.queue.poll();
            if (record == null) {
                break;
            }
            batch.add(record);
        }

        if (!batch.isEmpty()) {
            buffer.pendingCount.addAndGet(-batch.size());
        }

        return batch;
    }

    private void recordFailure(RunBuffer buffer, Exception failure) {
        buffer.firstFailure.compareAndSet(
                null,
                new ExecutionPersistenceException(
                        "Persistence failed for runId=" + buffer.runId,
                        failure));
    }

    private void assertHealthy(RunBuffer buffer) {
        ExecutionPersistenceException failure = buffer.firstFailure.get();
        if (failure != null) {
            throw failure;
        }
    }

    private static final class RunBuffer {
        private final UUID runId;
        private final ConcurrentLinkedQueue<StationLogSnapshot> queue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger pendingCount = new AtomicInteger();
        private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final java.util.concurrent.locks.ReentrantLock flushLock = new java.util.concurrent.locks.ReentrantLock();
        private final AtomicReference<ExecutionPersistenceException> firstFailure = new AtomicReference<>();

        private RunBuffer(UUID runId) {
            this.runId = runId;
        }
    }

    private static final class Gear4jFlushThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "gear4j-db-flush-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
