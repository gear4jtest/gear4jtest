package io.github.gear4jtest.external.api.exception;

/** Raised when an external API request violates a stable input contract. */
public class ExternalValidationException extends Gear4jExternalException {
    public ExternalValidationException(String message) {
        super(ExternalErrorCode.VALIDATION, message);
    }

    public ExternalValidationException(String message, Throwable cause) {
        super(ExternalErrorCode.VALIDATION, message, cause);
    }
}
