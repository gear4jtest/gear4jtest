package io.github.gear4jtest.external.api.exception;

import java.util.Objects;

/**
 * Base runtime exception for failures exposed by the external assembly-line
 * API.
 */
public class Gear4jExternalException extends RuntimeException {
    private final ExternalErrorCode errorCode;

    public Gear4jExternalException(ExternalErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public Gear4jExternalException(ExternalErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    public ExternalErrorCode errorCode() {
        return errorCode;
    }
}
