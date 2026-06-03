package io.github.gear4jtest.core.exception;

public class PipelineCallException extends RuntimeException {
    public PipelineCallException(String message) {
        super(message);
    }

    public PipelineCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
