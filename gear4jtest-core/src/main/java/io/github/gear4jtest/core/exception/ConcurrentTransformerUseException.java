package io.github.gear4jtest.core.exception;

public class ConcurrentTransformerUseException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public ConcurrentTransformerUseException(String message) {
        super(message);
    }

    public ConcurrentTransformerUseException(String message, Throwable cause) {
        super(message, cause);
    }
}
