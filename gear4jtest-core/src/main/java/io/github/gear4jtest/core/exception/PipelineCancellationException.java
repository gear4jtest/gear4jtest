package io.github.gear4jtest.core.exception;

/** Exception used for cooperative cancellation of a running pipeline. */
public final class PipelineCancellationException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public PipelineCancellationException(String message) {
        super(message != null ? message : "Pipeline cancellation requested");
    }

    public PipelineCancellationException(String message, Throwable cause) {
        super(message != null ? message : "Pipeline cancellation requested", cause);
    }
}
