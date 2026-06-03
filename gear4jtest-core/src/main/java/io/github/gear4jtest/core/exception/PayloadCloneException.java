package io.github.gear4jtest.core.exception;

public final class PayloadCloneException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public PayloadCloneException(String message) {
        super(message);
    }

    public PayloadCloneException(String message, Throwable cause) {
        super(message, cause);
    }
}
