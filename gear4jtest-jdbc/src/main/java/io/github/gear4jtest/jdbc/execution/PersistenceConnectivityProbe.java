package io.github.gear4jtest.jdbc.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/** Executes readiness connectivity checks within one end-to-end deadline. */
final class PersistenceConnectivityProbe {
    private static final long IDLE_WORKER_TIMEOUT_SECONDS = 30L;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, IDLE_WORKER_TIMEOUT_SECONDS,
            TimeUnit.SECONDS, new SynchronousQueue<>(), PersistenceThreadFactories.connectivityProbe(),
            new ThreadPoolExecutor.AbortPolicy());

    boolean execute(Duration timeout, BooleanSupplier connectivityCheck) {
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(connectivityCheck, "connectivityCheck must not be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be > 0");
        }

        Future<Boolean> probe;
        try {
            probe = executor.submit(connectivityCheck::getAsBoolean);
        } catch (RejectedExecutionException rejectedExecutionException) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(probe.get(toNanosSaturated(timeout), TimeUnit.NANOSECONDS));
        } catch (TimeoutException timeoutException) {
            probe.cancel(true);
            return false;
        } catch (InterruptedException interruptedException) {
            probe.cancel(true);
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException executionException) {
            Throwable cause = executionException.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Connectivity probe failed unexpectedly", cause);
        }
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private static long toNanosSaturated(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException arithmeticException) {
            return Long.MAX_VALUE;
        }
    }
}
