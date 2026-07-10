package io.github.gear4jtest.external.api.repository;

/** Raised when a publication key already points to different content. */
public final class OperationChainPublicationConflictException extends OperationChainRepositoryException {
    public OperationChainPublicationConflictException(String message) {
        super(message);
    }

    public OperationChainPublicationConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
