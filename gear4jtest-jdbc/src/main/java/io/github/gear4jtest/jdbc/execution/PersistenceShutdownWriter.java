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
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.jdbc.persistence.DatabaseAssemblyRunRepository;

/**
 * Performs deadline-bound JDBC writes used exclusively by persistence shutdown.
 */
final class PersistenceShutdownWriter {
    private final DatabaseAssemblyRunRepository repository;
    private final PersistenceRuntimeConfiguration configuration;
    private final PersistenceBatchProcessor batchProcessor;

    PersistenceShutdownWriter(DatabaseAssemblyRunRepository repository,
                              PersistenceRuntimeConfiguration configuration,
                              PersistenceBatchProcessor batchProcessor) {
        this.repository = repository;
        this.configuration = configuration;
        this.batchProcessor = batchProcessor;
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
            while (true) {
                if (deadline.reached()) {
                    return FlushOutcome.deadline(deadlineFailure(buffer, "starting the next JDBC operation"));
                }
                List<StationLogRecord> batch = buffer.drainBatch();
                if (batch.isEmpty()) {
                    break;
                }
                batchProcessor.persist(buffer, batch,
                                       records -> persistBatch(records, buffer, deadline, executor),
                                       (record, context) -> persistRejectedRecord(record, context, buffer, deadline,
                                                                                  executor));
            }
            if (buffer.isFinalizationPending()) {
                persistFinalRecord(buffer.finalRecord(), buffer, deadline, executor);
                buffer.completeFinalization();
            }
            return FlushOutcome.success();
        } catch (ShutdownDeadlineException exception) {
            return FlushOutcome.deadline(exception);
        } catch (ShutdownInterruptedException exception) {
            Thread.currentThread().interrupt();
            return FlushOutcome.interrupted(exception);
        } catch (RuntimeException exception) {
            return FlushOutcome.retryable(exception);
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

    private void persistBatch(List<StationLogRecord> batch,
                              OperationRecordBuffer buffer,
                              PersistenceShutdownDeadline deadline,
                              ExecutorService executor) {
        executeWithinDeadline(() -> repository.saveOperationRecordsBatch(batch), buffer, deadline, executor,
                              "waiting for JDBC batch completion");
    }

    private void persistFinalRecord(AssemblyRunRecord record,
                                    OperationRecordBuffer buffer,
                                    PersistenceShutdownDeadline deadline,
                                    ExecutorService executor) {
        try {
            executeWithinDeadline(() -> repository.update(record), buffer, deadline, executor,
                                  "waiting for run finalization");
        } catch (RuntimeException exception) {
            buffer.recordFinalizationFailure(exception);
            throw exception;
        }
    }

    private void persistRejectedRecord(StationLogRecord record,
                                       RejectedPersistenceRecordContext context,
                                       OperationRecordBuffer buffer,
                                       PersistenceShutdownDeadline deadline,
                                       ExecutorService executor) {
        executeWithinDeadline(() -> batchProcessor.invokeRejectedRecordHandler(record, context), buffer, deadline,
                              executor, "waiting for rejected-record handling");
    }

    private void executeWithinDeadline(Runnable write,
                                       OperationRecordBuffer buffer,
                                       PersistenceShutdownDeadline deadline,
                                       ExecutorService executor,
                                       String activity) {
        Future<?> future;
        try {
            future = executor.submit(write);
        } catch (RejectedExecutionException exception) {
            throw new ExecutionPersistenceException(
                    "Persistence shutdown JDBC worker rejected work for runId=" + buffer.runId(), exception);
        }
        try {
            future.get(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new ShutdownDeadlineException(deadlineFailure(buffer, activity));
        } catch (InterruptedException exception) {
            future.cancel(true);
            throw new ShutdownInterruptedException(new ExecutionPersistenceException(
                    "Persistence shutdown was interrupted while writing runId=" + buffer.runId(), exception));
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new ExecutionPersistenceException("Persistence shutdown JDBC work failed for runId="
                    + buffer.runId(), cause);
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

        private static FlushOutcome interrupted(Exception failure) {
            return new FlushOutcome(false, false, true, false, failure);
        }

        private static FlushOutcome deadline(Exception failure) {
            return new FlushOutcome(false, false, false, true, failure);
        }
    }

    private static final class ShutdownDeadlineException extends ExecutionPersistenceException {
        private static final long serialVersionUID = 1L;

        private ShutdownDeadlineException(ExecutionPersistenceException cause) {
            super(cause.getMessage(), cause);
        }
    }

    private static final class ShutdownInterruptedException extends ExecutionPersistenceException {
        private static final long serialVersionUID = 1L;

        private ShutdownInterruptedException(ExecutionPersistenceException cause) {
            super(cause.getMessage(), cause);
        }
    }
}
