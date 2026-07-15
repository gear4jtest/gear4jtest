package io.github.gear4jtest.jdbc.execution;

import java.util.Objects;
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
    private volatile boolean open = true;
    private int inFlightOperations;

    boolean isOpen() {
        return open;
    }

    void ensureOpen() {
        if (!open) {
            throw closedException();
        }
    }

    void executeWhileOpen(Runnable operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        enter();
        try {
            operation.run();
        } finally {
            leave();
        }
    }

    boolean closeAdmissionAndAwaitIdle() {
        boolean interrupted = false;
        lifecycleLock.lock();
        try {
            open = false;
            while (inFlightOperations > 0) {
                try {
                    idle.await();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return interrupted;
    }

    private void enter() {
        lifecycleLock.lock();
        try {
            if (!open) {
                throw closedException();
            }
            inFlightOperations++;
        } finally {
            lifecycleLock.unlock();
        }
    }

    private void leave() {
        lifecycleLock.lock();
        try {
            inFlightOperations--;
            if (inFlightOperations == 0) {
                idle.signalAll();
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    private static ExecutionPersistenceException closedException() {
        return new ExecutionPersistenceException("DatabaseExecutionManager is already shut down");
    }
}
