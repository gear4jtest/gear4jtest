package io.github.gear4jtest.core.event.transport;

/**
 * Raised when forwarding an event to an external transport fails or is
 * rejected.
 *
 * <p>
 * When this exception is thrown from an asynchronous event reaction attached to
 * the current in-memory runtime, the failure is treated as a best-effort
 * reaction failure. No durable retry is provided by the core runtime.
 * </p>
 */
public class ExternalTransportPublishException extends RuntimeException {
    public ExternalTransportPublishException(String message) {
        super(message);
    }

    public ExternalTransportPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
