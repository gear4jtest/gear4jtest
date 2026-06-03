package io.github.gear4jtest.core.api.context;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.github.gear4jtest.core.exception.PipelineCancellationException;

/**
 * Cooperative cancellation signal shared by all stations of one run.
 *
 * <p>
 * Gear4J may interrupt asynchronous tasks, but user operators that perform long
 * blocking or iterative work should also poll this token and stop cleanly when
 * cancellation is requested.
 * </p>
 */
public final class CancellationToken {
    private final AtomicReference<PipelineCancellationException> cancellation = new AtomicReference<>();

    /** Requests cancellation once and preserves the first reason. */
    public boolean cancel(String reason) {
        return cancel(new PipelineCancellationException(reason));
    }

    /** Requests cancellation once and preserves the first cause. */
    public boolean cancel(PipelineCancellationException cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cancellation cause must not be null");
        }
        return cancellation.compareAndSet(null, cause);
    }

    public boolean isCancellationRequested() {
        return cancellation.get() != null;
    }

    public Optional<PipelineCancellationException> cancellationCause() {
        return Optional.ofNullable(cancellation.get());
    }

    /**
     * Throws the recorded cancellation exception when cancellation was requested.
     */
    public void throwIfCancellationRequested() {
        PipelineCancellationException cause = cancellation.get();
        if (cause != null) {
            throw cause;
        }
    }
}
