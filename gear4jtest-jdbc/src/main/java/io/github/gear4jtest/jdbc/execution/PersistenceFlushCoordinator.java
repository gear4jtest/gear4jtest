package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
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
import java.util.concurrent.locks.ReentrantLock;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeStats;
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
    private final PersistenceShutdownWriter shutdownWriter;
    private final ScheduledFuture<?> periodicFlushTask;
    private final PersistenceOperationGate operationGate = new PersistenceOperationGate();
    private final ReentrantLock shutdownLock = new ReentrantLock();
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
        this.shutdownWriter = new PersistenceShutdownWriter(repository, configuration, counters);
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
        return counters.snapshot(buffers, !operationGate.isOpen());
    }

    boolean isShutdown() {
        return !operationGate.isOpen();
    }

    PersistenceShutdownReport shutdownReport() {
        return shutdownReport;
    }

    void ensureOpen() {
        operationGate.ensureOpen();
    }

    void executeWhileOpen(UUID runId, Runnable operation) {
        operationGate.executeWhileOpen(List.of(runId), operation);
    }

    void executeWhileOpen(List<UUID> runIds, Runnable operation) {
        operationGate.executeWhileOpen(runIds, operation);
    }

    void scheduleAsyncFlush(OperationRecordBuffer buffer, boolean drainCompletely) {
        if (!buffer.markFlushScheduled()) {
            return;
        }
        if (!operationGate.isOpen()) {
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
        if (operationGate.isOpen() && buffer.pendingCount() >= configuration.batchSize()) {
            scheduleAsyncFlush(buffer, false);
        }
    }

    PersistenceShutdownReport shutdown(Duration timeout) {
        PersistenceShutdownDeadline deadline = PersistenceShutdownDeadline.start(timeout);
        if (!acquireShutdownLock(deadline)) {
            throw new ExecutionPersistenceException("Persistence shutdown is already in progress and did not finish "
                    + "within the supplied timeout");
        }
        try {
            if (shutdownReport != null) {
                return shutdownReport;
            }
            shutdownReport = performShutdown(deadline);
            return shutdownReport;
        } finally {
            shutdownLock.unlock();
        }
    }

    private PersistenceShutdownReport performShutdown(PersistenceShutdownDeadline deadline) {
        PersistenceOperationGate.IdleWaitResult idleWait = operationGate.closeAdmissionAndAwaitIdle(deadline);
        boolean interrupted = idleWait.interrupted();
        int initialActiveRuns = buffers.activeRunCount();
        int initialBufferedStationLogs = buffers.bufferedStationLogCount();

        periodicFlushTask.cancel(false);
        if (ownsMaintenanceExecutor) {
            maintenanceExecutor.shutdownNow();
        }
        int droppedFlushTasks = 0;
        if (ownsFlushExecutor) {
            droppedFlushTasks = flushExecutor.shutdownNow().size();
        }

        Map<UUID, PersistenceShutdownRunState> runStates = idleWait.idle() ? shutdownRunStates() : Map.of();
        ExecutorService shutdownJdbcExecutor = shutdownWriter.createExecutor();
        int flushAttempts = 0;
        try {
            if (idleWait.idle() && !deadline.reached()) {
                flushAttempts = attemptInitialShutdownFlushes(runStates, deadline, shutdownJdbcExecutor);
                while (hasRetryablePendingLogs(runStates) && !deadline.reached()) {
                    RetryPass retryPass = retryEligibleBuffers(runStates, deadline, shutdownJdbcExecutor);
                    flushAttempts += retryPass.attempts();
                    if (!retryPass.attempted() && !sleepUntilNextRetry(runStates, deadline)) {
                        interrupted = true;
                        break;
                    }
                }
            }
        } finally {
            shutdownJdbcExecutor.shutdownNow();
        }

        PersistenceShutdownWriter.ExecutorTermination asyncTermination = ownsFlushExecutor
                ? shutdownWriter.awaitTermination(flushExecutor, deadline, droppedFlushTasks)
                : new PersistenceShutdownWriter.ExecutorTermination(true, false, droppedFlushTasks);
        PersistenceShutdownWriter.ExecutorTermination shutdownTermination = shutdownWriter
                .awaitTermination(shutdownJdbcExecutor, deadline, 0);
        interrupted = interrupted || asyncTermination.interrupted() || shutdownTermination.interrupted();

        List<PersistenceShutdownReport.RunFailure> failures = idleWait.idle()
                ? finalizeRemainingBuffers(runStates)
                : unfinishedOperationFailures(idleWait.unfinishedOperations());
        int remainingActiveRuns = buffers.activeRunCount();
        int remainingStationLogs = buffers.bufferedStationLogCount();
        boolean executorsTerminated = asyncTermination.terminated() && shutdownTermination.terminated();
        boolean incomplete = !idleWait.unfinishedOperations().isEmpty() || remainingStationLogs > 0
                || !executorsTerminated;
        boolean reached = incomplete && deadline.reached();
        int flushedStationLogs = Math.max(0, initialBufferedStationLogs - remainingStationLogs);
        return new PersistenceShutdownReport(deadline.startedAt(), deadline.elapsed(), initialActiveRuns,
                initialBufferedStationLogs, flushedStationLogs, remainingActiveRuns, remainingStationLogs,
                flushAttempts, reached, interrupted, executorsTerminated, asyncTermination.droppedTasks(), failures);
    }

    private boolean acquireShutdownLock(PersistenceShutdownDeadline deadline) {
        try {
            return shutdownLock.tryLock(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Map<UUID, PersistenceShutdownRunState> shutdownRunStates() {
        Map<UUID, PersistenceShutdownRunState> runStates = new LinkedHashMap<>();
        for (OperationRecordBuffer buffer : new ArrayList<>(buffers.activeBuffers())) {
            boolean finalizationPending = buffer.isClosed();
            buffer.close();
            runStates.put(buffer.runId(), new PersistenceShutdownRunState(buffer,
                    configuration.shutdownRetryInitialBackoff(), finalizationPending));
        }
        return runStates;
    }

    private int attemptInitialShutdownFlushes(Map<UUID, PersistenceShutdownRunState> runStates,
                                              PersistenceShutdownDeadline deadline,
                                              ExecutorService shutdownJdbcExecutor) {
        int attempts = 0;
        for (PersistenceShutdownRunState state : runStates.values()) {
            if (deadline.reached()) {
                break;
            }
            PendingCheck pendingCheck = hasPendingLogsAfterInFlightFlush(state, deadline);
            if (pendingCheck.interrupted()) {
                break;
            }
            if (!pendingCheck.pending()) {
                if (!state.finalizationPending()) {
                    buffers.remove(state.buffer().runId());
                }
                continue;
            }
            if (deadline.reached()) {
                break;
            }
            attemptShutdownFlush(state, deadline, shutdownJdbcExecutor);
            attempts++;
        }
        return attempts;
    }

    private PendingCheck hasPendingLogsAfterInFlightFlush(PersistenceShutdownRunState state,
                                                          PersistenceShutdownDeadline deadline) {
        boolean locked = false;
        try {
            locked = state.buffer().tryLockFlush(deadline.remainingNanos());
            if (!locked) {
                state.recordDeadlineFailure(
                                            shutdownWriter.deadlineFailure(state.buffer(),
                                                                           "waiting for an in-flight buffer flush"));
                return new PendingCheck(true, false);
            }
            return new PendingCheck(state.buffer().pendingCount() > 0, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            state.recordTerminalFailure(new ExecutionPersistenceException(
                    "Persistence shutdown was interrupted while waiting for run buffer " + state.buffer().runId(),
                    exception));
            return new PendingCheck(true, true);
        } finally {
            if (locked) {
                state.buffer().unlockFlush();
            }
        }
    }

    private RetryPass retryEligibleBuffers(Map<UUID, PersistenceShutdownRunState> runStates,
                                           PersistenceShutdownDeadline deadline,
                                           ExecutorService shutdownJdbcExecutor) {
        int attempts = 0;
        long now = System.nanoTime();
        for (PersistenceShutdownRunState state : runStates.values()) {
            if (!state.retryable() || state.buffer().pendingCount() == 0 || now < state.nextAttemptNanos()) {
                continue;
            }
            attemptShutdownFlush(state, deadline, shutdownJdbcExecutor);
            attempts++;
            if (deadline.reached()) {
                break;
            }
            now = System.nanoTime();
        }
        return new RetryPass(attempts > 0, attempts);
    }

    private void attemptShutdownFlush(PersistenceShutdownRunState state,
                                      PersistenceShutdownDeadline deadline,
                                      ExecutorService shutdownJdbcExecutor) {
        state.recordAttempt();
        PersistenceShutdownWriter.FlushOutcome outcome = shutdownWriter.flushBuffer(state.buffer(), deadline,
                                                                                    shutdownJdbcExecutor);
        if (outcome.successful()) {
            if (state.buffer().retainedCount() == 0) {
                if (!state.finalizationPending()) {
                    buffers.remove(state.buffer().runId());
                }
                counters.recordCompletedFlush();
            }
            return;
        }

        counters.recordFailedFlush();
        if (outcome.retryable()) {
            state.recordRetryableFailure(outcome.failure(), configuration.shutdownRetryMaxBackoff());
        } else if (outcome.deadlineReached()) {
            state.recordDeadlineFailure(outcome.failure());
        } else {
            state.recordTerminalFailure(outcome.failure());
        }
        if (outcome.interrupted()) {
            Thread.currentThread().interrupt();
        }
        LOGGER.warn("JDBC persistence shutdown flush failed. runId={}, attempt={}, retryable={}, "
                + "remainingStationLogs={}", state.buffer().runId(), state.attempts(), outcome.retryable(),
                    state.buffer().pendingCount(), outcome.failure());
    }

    private boolean sleepUntilNextRetry(Map<UUID, PersistenceShutdownRunState> runStates,
                                        PersistenceShutdownDeadline deadline) {
        long wakeUpNanos = PersistenceShutdownDeadline.addSaturated(System.nanoTime(), deadline.remainingNanos());
        for (PersistenceShutdownRunState state : runStates.values()) {
            if (state.retryable() && state.buffer().pendingCount() > 0) {
                wakeUpNanos = Math.min(wakeUpNanos, state.nextAttemptNanos());
            }
        }
        long sleepNanos = Math.min(deadline.remainingNanos(), Math.max(0L, wakeUpNanos - System.nanoTime()));
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

    private List<PersistenceShutdownReport.RunFailure> unfinishedOperationFailures(
                                                                                   List<PersistenceOperationGate.InFlightOperation> unfinishedOperations) {
        List<PersistenceShutdownReport.RunFailure> failures = new ArrayList<>();
        for (PersistenceOperationGate.InFlightOperation operation : unfinishedOperations) {
            UUID runId = operation.diagnosticRunId();
            OperationRecordBuffer buffer = buffers.get(runId);
            int remaining = buffer != null ? buffer.retainedCount() : 0;
            failures.add(PersistenceShutdownReport.unfinishedOperation(runId, remaining, operation.runIds()));
        }
        return failures;
    }

    private List<PersistenceShutdownReport.RunFailure> finalizeRemainingBuffers(
                                                                                Map<UUID, PersistenceShutdownRunState> runStates) {
        List<PersistenceShutdownReport.RunFailure> failures = new ArrayList<>();
        for (PersistenceShutdownRunState state : runStates.values()) {
            int remaining = state.buffer().retainedCount();
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

    private boolean hasRetryablePendingLogs(Map<UUID, PersistenceShutdownRunState> runStates) {
        return runStates.values().stream()
                .anyMatch(state -> state.retryable() && state.buffer().pendingCount() > 0);
    }

    private void flushPendingBuffersSafely() {
        if (!operationGate.isOpen()) {
            return;
        }
        for (OperationRecordBuffer buffer : buffers.activeBuffers()) {
            if (buffer.pendingCount() > 0 && !buffer.isClosed()) {
                scheduleAsyncFlush(buffer, false);
            }
        }
    }

    private record RetryPass(boolean attempted, int attempts) {}

    private record PendingCheck(boolean pending, boolean interrupted) {}
}
