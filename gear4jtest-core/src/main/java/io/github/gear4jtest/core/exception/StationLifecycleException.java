package io.github.gear4jtest.core.exception;

/**
 * Diagnostic failure raised by a critical station lifecycle observer.
 *
 * <p>
 * This exception is recorded in the station trace; it is not intended to be
 * thrown directly through the public pipeline execution API.
 * </p>
 */
public final class StationLifecycleException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public StationLifecycleException(String callback, Class<?> extensionType, Exception cause) {
        super("Critical station lifecycle extension failed during " + callback + ". extension="
                + extensionType.getName() + ": " + cause.getMessage(), cause);
    }
}
