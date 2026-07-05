package io.github.gear4jtest.core.exception;

/**
 * Raised when a runtime resource required by a station cannot be resolved from
 * the configured resource factory.
 */
public final class ResourceResolutionException extends Gear4JException {
    private static final long serialVersionUID = 1L;

    public ResourceResolutionException(String message) {
        super(message);
    }

    public ResourceResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
