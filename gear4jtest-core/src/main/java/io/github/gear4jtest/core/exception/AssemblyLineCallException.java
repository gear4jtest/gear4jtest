package io.github.gear4jtest.core.exception;

public class AssemblyLineCallException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public AssemblyLineCallException(String message) {
        super(message);
    }

    public AssemblyLineCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
