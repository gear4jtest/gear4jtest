package io.github.gear4jtest.jdbc.execution;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;

/**
 * Performs deadline-bound JDBC writes used exclusively by persistence shutdown.
 */
final class PersistenceShutdownWriter {
    private final DatabaseAssemblyRunRepository repository;
    private final PersistenceRuntimeConfiguration configuration;
    private final PersistenceRuntimeCounters counters;

    PersistenceShutdownWriter(DatabaseAssemblyRunRepository repository,
                              PersistenceRuntimeConfiguration configuration,
                              PersistenceRuntimeCounters counters) {
        this.repository = repository;
        this.configuration = configuration;
        this.counters = counters;
    }

    ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(configuration.flushThreadCount(),
                                            PersistenceThreadFactories.shutdownWorker());
    }

    FlushOutcome flushBuffer(OperationRecordBuffer buffer,
                             PersistenceShutdownDeadline deadline,
                             ExecutorService executor) {
        boolean locked = false;
        try {
            locked = buffer.tryLockFlush(deadline.remainingNanos());
            if (!locked) {
                return FlushOutcome.deadline(deadlineFailure(buffer, "acquiring its flush lock"));
            }
            do {
                if (deadline.reached()) {
                    return FlushOutcome.deadline(deadlineFailure(buffer, "starting the next JDBC batch"));
                }
                List<StationLogRecord> batch = buffer.drainBatch(configuration.batchSize());
                if (batch.isEmpty()) {
                    return FlushOutcome.success();
                }
                FlushOutcome batchOutcome = persistBatch(buffer, batch, deadline, executor);
                if (!batchOutcome.successful()) {
                    return batchOutcome;
                }
                counters.recordSuccessfulFlushProgress();
                buffer.acknowledgeDrainedBatch(batch);
            } while (true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FlushOutcome.interrupted(new ExecutionPersistenceException(
                    "Persistence shutdown was interrupted while locking run buffer " + buffer.runId(), exception));
        } finally {
            buffer.clearFlushScheduled();
            if (locked) {
                buffer.unlockFlush();
            }
        }
    }

    ExecutorTermination awaitTermination(ExecutorService executor,
                                         PersistenceShutdownDeadline deadline,
                                         int droppedTasks) {
        try {
            boolean terminated = executor.awaitTermination(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            return new ExecutorTermination(terminated, false, droppedTasks);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ExecutorTermination(false, true, droppedTasks);
        }
    }

    ExecutionPersistenceException deadlineFailure(OperationRecordBuffer buffer, String activity) {
        return new ExecutionPersistenceException("Persistence shutdown deadline reached while " + activity
                + ". runId=" + buffer.runId() + ", remainingStationLogs=" + buffer.retainedCount());
    }

    private FlushOutcome persistBatch(OperationRecordBuffer buffer,
                                      List<StationLogRecord> batch,
                                      PersistenceShutdownDeadline deadline,
                                      ExecutorService executor) {
        Future<?> write;
        try {
            write = executor.submit(() -> repository.saveOperationRecordsBatch(batch));
        } catch (RejectedExecutionException exception) {
            buffer.restoreDrainedBatch(batch);
            return FlushOutcome.terminal(new ExecutionPersistenceException(
                    "Persistence shutdown JDBC worker rejected a batch for runId=" + buffer.runId(), exception));
        }
        try {
            write.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            return FlushOutcome.success();
        } catch (TimeoutException exception) {
            write.cancel(true);
            buffer.restoreDrainedBatch(batch);
            return FlushOutcome.deadline(deadlineFailure(buffer, "waiting for JDBC batch completion"));
        } catch (InterruptedException exception) {
            write.cancel(true);
            buffer.restoreDrainedBatch(batch);
            Thread.currentThread().interrupt();
            return FlushOutcome.interrupted(new ExecutionPersistenceException(
                    "Persistence shutdown was interrupted while writing runId=" + buffer.runId(), exception));
        } catch (ExecutionException exception) {
            buffer.restoreDrainedBatch(batch);
            Throwable cause = exception.getCause();
            Exception failure = cause instanceof Exception checked ? checked
                    : new ExecutionPersistenceException("Persistence shutdown JDBC batch failed for runId="
                            + buffer.runId(), cause);
            return FlushOutcome.retryable(failure);
        }
    }

    record ExecutorTermination(boolean terminated, boolean interrupted, int droppedTasks) {}

    record FlushOutcome(boolean successful,
                        boolean retryable,
                        boolean interrupted,
                        boolean deadlineReached,
                        Exception failure) {
        private static FlushOutcome success() {
            return new FlushOutcome(true, false, false, false, null);
        }

        private static FlushOutcome retryable(Exception failure) {
            return new FlushOutcome(false, true, false, false, failure);
        }

        private static FlushOutcome terminal(Exception failure) {
            return new FlushOutcome(false, false, false, false, failure);
        }

        private static FlushOutcome interrupted(Exception failure) {
            return new FlushOutcome(false, false, true, false, failure);
        }

        private static FlushOutcome deadline(Exception failure) {
            return new FlushOutcome(false, false, false, true, failure);
        }
    }
}
