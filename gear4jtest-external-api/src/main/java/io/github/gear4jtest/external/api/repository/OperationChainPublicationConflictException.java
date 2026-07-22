package io.github.gear4jtest.external.api.repository;

import io.github.gear4jtest.external.api.exception.ExternalErrorCode;

/** Raised when a publication key already points to different content. */
public final class OperationChainPublicationConflictException extends OperationChainRepositoryException {
    public OperationChainPublicationConflictException(String message) {
        super(ExternalErrorCode.CONFLICT, message);
    }

    public OperationChainPublicationConflictException(String message, Throwable cause) {
        super(ExternalErrorCode.CONFLICT, message, cause);
    }
}
