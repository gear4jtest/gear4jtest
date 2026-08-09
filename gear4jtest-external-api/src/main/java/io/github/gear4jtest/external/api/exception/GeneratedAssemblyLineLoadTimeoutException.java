package io.github.gear4jtest.external.api.exception;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * Raised when generated assembly-line loading exceeds its end-to-end deadline.
 *
 * <p>
 * Cancellation is best-effort because artifact stores, translators, compilers,
 * constructors, dependency injectors or registries may ignore thread
 * interruption. A late generated instance is never returned, and any completed
 * late registry publication is conditionally discarded before the single-flight
 * slot is released.
 * </p>
 */
public final class GeneratedAssemblyLineLoadTimeoutException extends IOException {
    private static final long serialVersionUID = 1L;

    private final String internalLoaderId;
    private final Duration timeout;

    public GeneratedAssemblyLineLoadTimeoutException(String internalLoaderId, Duration timeout) {
        super("Generated assembly-line loading timed out after "
                + Objects.requireNonNull(timeout, "timeout must not be null")
                + " for " + Objects.requireNonNull(internalLoaderId, "internalLoaderId must not be null"));
        this.internalLoaderId = internalLoaderId;
        this.timeout = timeout;
    }

    public String internalLoaderId() {
        return internalLoaderId;
    }

    public Duration timeout() {
        return timeout;
    }
}
