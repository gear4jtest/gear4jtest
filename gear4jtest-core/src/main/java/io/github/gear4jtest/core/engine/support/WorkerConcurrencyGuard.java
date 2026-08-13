package io.github.gear4jtest.core.engine.support;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.github.gear4jtest.core.api.config.WorkerConcurrencyConfiguration;
import io.github.gear4jtest.core.api.config.WorkerLockAcquisitionPolicy;
import io.github.gear4jtest.core.exception.ConcurrentTransformerUseException;
import io.github.gear4jtest.core.util.MonotonicDeadline;

public final class WorkerConcurrencyGuard {
    private final ReentrantLock monitor = new ReentrantLock();
    private final Condition available = monitor.newCondition();
    private boolean inUse;
    private Thread owner;

    /**
     * Acquires the guard before the full station lifecycle starts, blocking until
     * the worker becomes available or the default timeout elapses.
     */
    public void beforeUse() {
        beforeUse(WorkerLockAcquisitionPolicy.BLOCK_CALLER,
                  WorkerConcurrencyConfiguration.DEFAULT_LOCK_WAIT_TIMEOUT);
    }

    /**
     * Acquires the guard before the full station lifecycle starts.
     */
    public void beforeUse(WorkerLockAcquisitionPolicy acquisitionPolicy) {
        beforeUse(acquisitionPolicy, WorkerConcurrencyConfiguration.DEFAULT_LOCK_WAIT_TIMEOUT);
    }

    /**
     * Acquires the guard before the full station lifecycle starts.
     */
    public void beforeUse(WorkerLockAcquisitionPolicy acquisitionPolicy, Duration lockWaitTimeout) {
        Objects.requireNonNull(acquisitionPolicy, "acquisitionPolicy must not be null");
        Objects.requireNonNull(lockWaitTimeout, "lockWaitTimeout must not be null");
        if (lockWaitTimeout.isZero() || lockWaitTimeout.isNegative()) {
            throw new IllegalArgumentException("lockWaitTimeout must be strictly positive");
        }

        switch (acquisitionPolicy) {
            case BLOCK_CALLER -> lockWithTimeout(lockWaitTimeout);
            case FAIL_FAST -> lockFailFast();
        }
    }

    /**
     * Releases the guard after the full station lifecycle has completed.
     */
    public void afterUse() {
        monitor.lock();
        try {
            if (!inUse) {
                throw new ConcurrentTransformerUseException("Worker lock is not held");
            }
            if (owner != Thread.currentThread()) {
                throw new ConcurrentTransformerUseException("Worker lock is held by another thread");
            }

            inUse = false;
            owner = null;
            available.signal();
        } finally {
            monitor.unlock();
        }
    }

    private void lockFailFast() {
        monitor.lock();
        try {
            if (inUse) {
                if (owner == Thread.currentThread()) {
                    throw new ConcurrentTransformerUseException("Reentrant worker lock acquisition is not supported");
                }
                throw new ConcurrentTransformerUseException("Worker lock is already held");
            }
            markInUseByCurrentThread();
        } finally {
            monitor.unlock();
        }
    }

    private void lockWithTimeout(Duration lockWaitTimeout) {
        MonotonicDeadline deadline = MonotonicDeadline.start(lockWaitTimeout);
        monitor.lock();
        try {
            while (inUse) {
                if (owner == Thread.currentThread()) {
                    throw new ConcurrentTransformerUseException("Reentrant worker lock acquisition is not supported");
                }
                long remainingNanos = deadline.remainingNanos();
                if (remainingNanos <= 0L) {
                    throw new ConcurrentTransformerUseException("Timed out after " + lockWaitTimeout
                            + " while waiting for worker lock");
                }
                try {
                    long remainingAfterWait = available.awaitNanos(remainingNanos);
                    if (remainingAfterWait <= 0L && inUse) {
                        throw new ConcurrentTransformerUseException("Timed out after " + lockWaitTimeout
                                + " while waiting for worker lock");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConcurrentTransformerUseException("Interrupted while waiting for worker lock", e);
                }
            }
            markInUseByCurrentThread();
        } finally {
            monitor.unlock();
        }
    }

    private void markInUseByCurrentThread() {
        inUse = true;
        owner = Thread.currentThread();
    }
}
