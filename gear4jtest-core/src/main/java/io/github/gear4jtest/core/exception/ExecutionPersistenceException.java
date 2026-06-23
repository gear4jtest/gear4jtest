package io.github.gear4jtest.core.exception;

/**
 * Signals a failure in an {@code AssemblyRunRepository} implementation or in a
 * persistence-side runtime component.
 *
 * <p>
 * The core module exposes this type as part of the persistence SPI contract so
 * repository implementations can report durable storage failures with a stable,
 * Gear4J-specific unchecked exception. External persistence modules such as
 * JDBC should prefer this type over generic {@link IllegalStateException} or
 * raw driver exceptions when an operation cannot be completed reliably.
 * </p>
 */
public class ExecutionPersistenceException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public ExecutionPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExecutionPersistenceException(String message) {
        super(message);
    }
}
