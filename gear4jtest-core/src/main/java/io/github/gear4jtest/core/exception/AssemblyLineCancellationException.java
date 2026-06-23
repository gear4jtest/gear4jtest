package io.github.gear4jtest.core.exception;

/** Exception used for cooperative cancellation of a running pipeline. */
public final class AssemblyLineCancellationException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public AssemblyLineCancellationException(String message) {
        super(message != null ? message : "AssemblyLine cancellation requested");
    }

    public AssemblyLineCancellationException(String message, Throwable cause) {
        super(message != null ? message : "AssemblyLine cancellation requested", cause);
    }
}
