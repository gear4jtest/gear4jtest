package io.github.gear4jtest.core.api.context;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.exception.AssemblyLineCancellationException;

/**
 * Cooperative cancellation signal shared by all stations of one run.
 *
 * <p>
 * Gear4J may interrupt asynchronous tasks, but user operators that perform long
 * blocking or iterative work should also poll this token and stop cleanly when
 * cancellation is requested. The token is one-shot and stateful: sharing the
 * same instance between independent top-level runs means cancelling one of
 * those runs cancels every run that reused the token.
 * </p>
 */
public final class CancellationToken {
    private final AtomicReference<AssemblyLineCancellationException> cancellation = new AtomicReference<>();

    /** Requests cancellation once and preserves the first reason. */
    public boolean cancel(String reason) {
        return cancel(new AssemblyLineCancellationException(reason));
    }

    /** Requests cancellation once and preserves the first cause. */
    public boolean cancel(AssemblyLineCancellationException cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cancellation cause must not be null");
        }
        return cancellation.compareAndSet(null, cause);
    }

    public boolean isCancellationRequested() {
        return cancellation.get() != null;
    }

    public Optional<AssemblyLineCancellationException> cancellationCause() {
        return Optional.ofNullable(cancellation.get());
    }

    /**
     * Throws the recorded cancellation exception when cancellation was requested.
     */
    public void throwIfCancellationRequested() {
        AssemblyLineCancellationException cause = cancellation.get();
        if (cause != null) {
            throw cause;
        }
    }
}
