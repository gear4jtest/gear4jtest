package io.github.gear4jtest.core.exception;

/**
 * Raised when a worker instance protected by Gear4J's concurrency guard is
 * invoked concurrently in a way that violates its configured worker lock
 * policy.
 */
public class ConcurrentTransformerUseException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public ConcurrentTransformerUseException(String message) {
        super(message);
    }

    public ConcurrentTransformerUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
