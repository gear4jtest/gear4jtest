package io.github.gear4jtest.core.exception;

public class ExecutionPersistenceException extends Gear4JException {

    private static final long serialVersionUID = 1L;

    public ExecutionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutionPersistenceException(String message) {
        super(message);
    }
}
