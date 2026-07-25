package io.github.gear4jtest.external.api.exception;

import java.time.Duration;
import java.util.Objects;

/**
 * Raised when generated-source compilation exceeds its end-to-end deadline.
 *
 * <p>
 * Cancellation is best-effort because a compiler implementation may ignore
 * thread interruption.
 * </p>
 */
public final class CompilationTimeoutException extends CompilationException {
    private static final long serialVersionUID = 1L;

    private final String className;
    private final Duration timeout;

    public CompilationTimeoutException(String className, Duration timeout) {
        super("Generated-source compilation timed out after "
                + Objects.requireNonNull(timeout, "timeout must not be null")
                + " for " + Objects.requireNonNull(className, "className must not be null"));
        this.className = className;
        this.timeout = timeout;
    }

    public String className() {
        return className;
    }

    public Duration timeout() {
        return timeout;
    }
}
