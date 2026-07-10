package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.execution.PersistenceRuntimeStats;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;
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
    private volatile PersistenceShutdownReport shutdownReport;

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
        return counters.snapshot(buffers, shutdown.get());
    }

    boolean isShutdown() {
        return shutdown.get();
    }

    PersistenceShutdownReport shutdownReport() {
        return shutdownReport;
    }

    void ensureOpen() {
        if (shutdown.get()) {
            throw new ExecutionPersistenceException("DatabaseExecutionManager is already shut down");
        }
    }

    synchronized void executeWhileOpen(Runnable operation) {
        ensureOpen();
        operation.run();
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
        flushBufferBlocking(buffer, drainCompletely, true, drainCompletely);
    }

    private void flushBufferForShutdown(OperationRecordBuffer buffer) {
        flushBufferBlocking(buffer, true, false, false);
    }

    private void flushBufferBlocking(OperationRecordBuffer buffer,
                                     boolean drainCompletely,
                                     boolean requireHealthy,
                                     boolean recordTerminalFailure) {
        buffer.lockFlush();
        try {
            if (requireHealthy) {
                buffer.assertHealthy();
            }
            do {
                List<StationLogRecord> batch = buffer.drainBatch(configuration.batchSize());
                if (batch.isEmpty()) {
                    return;
                }
                try {
                    repository.saveOperationRecordsBatch(batch);
                    counters.recordSuccessfulFlushProgress();
                    buffer.acknowledgeDrainedBatch(batch);
                } catch (Exception e) {
                    buffer.restoreDrainedBatch(batch);
                    if (recordTerminalFailure) {
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

    synchronized PersistenceShutdownReport shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (shutdownReport != null) {
            return shutdownReport;
        }
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        long deadlineNanos = deadlineAfter(startedNanos, timeout);
        int initialActiveRuns = buffers.activeRunCount();
        int initialBufferedStationLogs = buffers.bufferedStationLogCount();
        if (!shutdown.compareAndSet(false, true)) {
            return shutdownReport;
        }
        periodicFlushTask.cancel(false);
        if (ownsMaintenanceExecutor) {
            maintenanceExecutor.shutdownNow();
        }
        int droppedFlushTasks = 0;
        if (ownsFlushExecutor) {
            droppedFlushTasks = flushExecutor.shutdownNow().size();
        }

        Map<UUID, ShutdownRunState> runStates = shutdownRunStates();
        int flushAttempts = attemptInitialShutdownFlushes(runStates);
        boolean interrupted = false;
        while (hasPendingLogs(runStates) && !deadlineReached(deadlineNanos)) {
            RetryPass retryPass = retryEligibleBuffers(runStates, deadlineNanos);
            flushAttempts += retryPass.attempts();
            if (!retryPass.attempted() && !sleepUntilNextRetry(runStates, deadlineNanos)) {
                interrupted = true;
                break;
            }
        }

        ExecutorTermination executorTermination = ownsFlushExecutor
                ? awaitFlushExecutorTermination(deadlineNanos, droppedFlushTasks)
                : new ExecutorTermination(true, interrupted, droppedFlushTasks);
        interrupted = interrupted || executorTermination.interrupted();

        List<PersistenceShutdownReport.RunFailure> failures = finalizeRemainingBuffers(runStates);
        int remainingActiveRuns = buffers.activeRunCount();
        int remainingStationLogs = buffers.bufferedStationLogCount();
        boolean deadlineReached = (remainingStationLogs > 0 || !executorTermination.terminated())
                && deadlineReached(deadlineNanos);
        int flushedStationLogs = Math.max(0, initialBufferedStationLogs - remainingStationLogs);
        shutdownReport = new PersistenceShutdownReport(startedAt,
                Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos)), initialActiveRuns,
                initialBufferedStationLogs, flushedStationLogs, remainingActiveRuns, remainingStationLogs,
                flushAttempts, deadlineReached, interrupted, executorTermination.terminated(),
                executorTermination.droppedTasks(), failures);
        return shutdownReport;
    }

    private Map<UUID, ShutdownRunState> shutdownRunStates() {
        Map<UUID, ShutdownRunState> runStates = new LinkedHashMap<>();
        long initialBackoffNanos = safeToNanos(configuration.shutdownRetryInitialBackoff());
        for (OperationRecordBuffer buffer : new ArrayList<>(buffers.activeBuffers())) {
            boolean finalizationPending = buffer.isClosed();
            buffer.close();
            runStates.put(buffer.runId(), new ShutdownRunState(buffer, initialBackoffNanos, finalizationPending));
        }
        return runStates;
    }

    private int attemptInitialShutdownFlushes(Map<UUID, ShutdownRunState> runStates) {
        int attempts = 0;
        for (ShutdownRunState state : runStates.values()) {
            if (!hasPendingLogsAfterInFlightFlush(state.buffer())) {
                if (!state.finalizationPending()) {
                    buffers.remove(state.buffer().runId());
                }
                continue;
            }
            attemptShutdownFlush(state);
            attempts++;
        }
        return attempts;
    }

    private boolean hasPendingLogsAfterInFlightFlush(OperationRecordBuffer buffer) {
        buffer.lockFlush();
        try {
            return buffer.pendingCount() > 0;
        } finally {
            buffer.unlockFlush();
        }
    }

    private RetryPass retryEligibleBuffers(Map<UUID, ShutdownRunState> runStates, long deadlineNanos) {
        int attempts = 0;
        long now = System.nanoTime();
        for (ShutdownRunState state : runStates.values()) {
            if (state.buffer().pendingCount() == 0 || now < state.nextAttemptNanos()) {
                continue;
            }
            attemptShutdownFlush(state);
            attempts++;
            if (deadlineReached(deadlineNanos)) {
                break;
            }
            now = System.nanoTime();
        }
        return new RetryPass(attempts > 0, attempts);
    }

    private void attemptShutdownFlush(ShutdownRunState state) {
        state.recordAttempt();
        try {
            flushBufferForShutdown(state.buffer());
            if (state.buffer().pendingCount() == 0) {
                if (!state.finalizationPending()) {
                    buffers.remove(state.buffer().runId());
                }
                counters.recordCompletedFlush();
            }
        } catch (Exception exception) {
            counters.recordFailedFlush();
            state.recordFailure(exception, configuration.shutdownRetryMaxBackoff());
            LOGGER.warn("JDBC persistence shutdown flush failed and may be retried. runId={}, attempt={}, "
                    + "remainingStationLogs={}", state.buffer().runId(), state.attempts(),
                        state.buffer().pendingCount(), exception);
        }
    }

    private boolean sleepUntilNextRetry(Map<UUID, ShutdownRunState> runStates, long deadlineNanos) {
        long wakeUpNanos = deadlineNanos;
        for (ShutdownRunState state : runStates.values()) {
            if (state.buffer().pendingCount() > 0) {
                wakeUpNanos = Math.min(wakeUpNanos, state.nextAttemptNanos());
            }
        }
        long sleepNanos = Math.min(remainingNanos(deadlineNanos), Math.max(0L, wakeUpNanos - System.nanoTime()));
        if (sleepNanos <= 0L) {
            return true;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(sleepNanos);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<PersistenceShutdownReport.RunFailure> finalizeRemainingBuffers(
                                                                                Map<UUID, ShutdownRunState> runStates) {
        List<PersistenceShutdownReport.RunFailure> failures = new ArrayList<>();
        for (ShutdownRunState state : runStates.values()) {
            int remaining = state.buffer().pendingCount();
            if (remaining == 0 && !state.finalizationPending()) {
                buffers.remove(state.buffer().runId());
                continue;
            }
            Exception failure = state.lastFailure();
            if (failure == null) {
                failure = state.buffer().currentFinalizationFailure();
            }
            if (failure == null) {
                failure = state.buffer().currentFailure();
            }
            if (failure == null) {
                String reason = state.finalizationPending()
                        ? "Run finalization was still incomplete at persistence shutdown. runId="
                        : "Persistence shutdown deadline reached. runId=";
                failure = new ExecutionPersistenceException(reason + state.buffer().runId()
                        + ", remainingStationLogs=" + remaining);
            }
            state.buffer().recordFailure(failure);
            failures.add(new PersistenceShutdownReport.RunFailure(state.buffer().runId(), state.attempts(), remaining,
                    failure.getClass().getName(), failure.getMessage()));
            LOGGER.error("Persistence shutdown left run data or finalization incomplete. runId={}, attempts={}, "
                    + "remainingStationLogs={}", state.buffer().runId(), state.attempts(), remaining, failure);
        }
        return failures;
    }

    private boolean hasPendingLogs(Map<UUID, ShutdownRunState> runStates) {
        return runStates.values().stream().anyMatch(state -> state.buffer().pendingCount() > 0);
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

    private ExecutorTermination awaitFlushExecutorTermination(long deadlineNanos, int droppedTasks) {
        try {
            boolean terminated = flushExecutor.awaitTermination(remainingNanos(deadlineNanos), TimeUnit.NANOSECONDS);
            return new ExecutorTermination(terminated, false, droppedTasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutorTermination(false, true, droppedTasks);
        }
    }

    private static long deadlineAfter(long startedNanos, Duration timeout) {
        long timeoutNanos = safeToNanos(timeout);
        if (timeoutNanos == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        try {
            return Math.addExact(startedNanos, timeoutNanos);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeToNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean deadlineReached(long deadlineNanos) {
        return deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos;
    }

    private static long remainingNanos(long deadlineNanos) {
        return deadlineNanos == Long.MAX_VALUE ? Long.MAX_VALUE
                : Math.max(0L, deadlineNanos - System.nanoTime());
    }

    private static long nextBackoff(long currentBackoffNanos, Duration maxBackoff) {
        long maxBackoffNanos = safeToNanos(maxBackoff);
        if (currentBackoffNanos >= maxBackoffNanos || currentBackoffNanos > Long.MAX_VALUE / 2L) {
            return maxBackoffNanos;
        }
        return Math.min(maxBackoffNanos, currentBackoffNanos * 2L);
    }

    private static long addSaturated(long base, long increment) {
        if (increment == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        try {
            return Math.addExact(base, increment);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static final class ShutdownRunState {
        private final OperationRecordBuffer buffer;
        private final boolean finalizationPending;
        private int attempts;
        private Exception lastFailure;
        private long nextAttemptNanos;
        private long backoffNanos;

        private ShutdownRunState(OperationRecordBuffer buffer,
                                 long initialBackoffNanos,
                                 boolean finalizationPending) {
            this.buffer = buffer;
            this.backoffNanos = initialBackoffNanos;
            this.finalizationPending = finalizationPending;
        }

        private OperationRecordBuffer buffer() {
            return buffer;
        }

        private int attempts() {
            return attempts;
        }

        private boolean finalizationPending() {
            return finalizationPending;
        }

        private Exception lastFailure() {
            return lastFailure;
        }

        private long nextAttemptNanos() {
            return nextAttemptNanos;
        }

        private void recordAttempt() {
            attempts++;
        }

        private void recordFailure(Exception failure, Duration maxBackoff) {
            this.lastFailure = failure;
            this.nextAttemptNanos = addSaturated(System.nanoTime(), backoffNanos);
            this.backoffNanos = nextBackoff(backoffNanos, maxBackoff);
        }
    }

    private record RetryPass(boolean attempted, int attempts) {}

    private record ExecutorTermination(boolean terminated, boolean interrupted, int droppedTasks) {}
}
