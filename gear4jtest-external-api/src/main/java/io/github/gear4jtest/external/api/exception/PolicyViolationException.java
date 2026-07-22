package io.github.gear4jtest.external.api.exception;

/** Raised when a publication or promotion violates a configured policy. */
public class PolicyViolationException extends Gear4jExternalException {
    public PolicyViolationException(String message) {
        super(ExternalErrorCode.VALIDATION, message);
    }

    public PolicyViolationException(String message, Throwable cause) {
        super(ExternalErrorCode.VALIDATION, message, cause);
    }
}
