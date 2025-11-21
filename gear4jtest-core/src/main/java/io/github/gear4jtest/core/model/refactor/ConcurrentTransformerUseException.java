package io.github.gear4jtest.core.model.refactor;

public class ConcurrentTransformerUseException extends RuntimeException {

    public ConcurrentTransformerUseException(String message) {
        super(message);
    }

    public ConcurrentTransformerUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
