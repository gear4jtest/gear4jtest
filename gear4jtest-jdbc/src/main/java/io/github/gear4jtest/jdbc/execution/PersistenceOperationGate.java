package io.github.gear4jtest.jdbc.execution;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;

/**
 * Coordinates persistence operation admission without holding a lock during the
 * operation itself.
 */
final class PersistenceOperationGate {
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final Condition idle = lifecycleLock.newCondition();
    private final ConcurrentMap<Long, InFlightOperation> inFlightOperations = new ConcurrentHashMap<>();
    private volatile boolean open = true;
    private long nextOperationId;

    boolean isOpen() {
        return open;
    }

    void ensureOpen() {
        if (!open) {
            throw closedException();
        }
    }

    void executeWhileOpen(List<UUID> runIds, Runnable operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        long operationId = enter(runIds);
        try {
            operation.run();
        } finally {
            leave(operationId);
        }
    }

    IdleWaitResult closeAdmissionAndAwaitIdle(PersistenceShutdownDeadline deadline) {
        Objects.requireNonNull(deadline, "deadline must not be null");
        open = false;
        boolean interrupted = false;
        boolean locked = false;
        try {
            locked = lifecycleLock.tryLock(deadline.remainingNanos(), TimeUnit.NANOSECONDS);
            if (!locked) {
                return new IdleWaitResult(false, false, snapshotWithoutLock());
            }
            while (!inFlightOperations.isEmpty() && !deadline.reached()) {
                try {
                    idle.awaitNanos(deadline.remainingNanos());
                } catch (InterruptedException exception) {
                    interrupted = true;
                    break;
                }
            }
            return new IdleWaitResult(inFlightOperations.isEmpty(), interrupted,
                    List.copyOf(inFlightOperations.values()));
        } catch (InterruptedException exception) {
            interrupted = true;
            return new IdleWaitResult(false, true, snapshotWithoutLock());
        } finally {
            if (locked) {
                lifecycleLock.unlock();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private long enter(List<UUID> runIds) {
        List<UUID> normalizedRunIds = List.copyOf(Objects.requireNonNull(runIds, "runIds must not be null"));
        if (normalizedRunIds.isEmpty()) {
            throw new IllegalArgumentException("runIds must not be empty");
        }
        lifecycleLock.lock();
        try {
            if (!open) {
                throw closedException();
            }
            long operationId = ++nextOperationId;
            inFlightOperations.put(operationId, new InFlightOperation(normalizedRunIds));
            return operationId;
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void leave(long operationId) {
        lifecycleLock.lock();
        try {
            inFlightOperations.remove(operationId);
            if (inFlightOperations.isEmpty()) {
                idle.signalAll();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private List<InFlightOperation> snapshotWithoutLock() {
        return List.copyOf(inFlightOperations.values());
    }

    private static ExecutionPersistenceException closedException() {
        return new ExecutionPersistenceException("DatabaseExecutionManager is already shut down");
    }

    record IdleWaitResult(boolean idle, boolean interrupted, List<InFlightOperation> unfinishedOperations) {}

    record InFlightOperation(List<UUID> runIds) {
        InFlightOperation {
            runIds = List.copyOf(runIds);
        }

        UUID diagnosticRunId() {
            return runIds.get(0);
        }
    }
}
