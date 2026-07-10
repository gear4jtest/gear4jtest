package io.github.gear4jtest.external.api.repository;

/** Base exception for failures raised by operation-chain repositories. */
public class OperationChainRepositoryException extends RuntimeException {
    public OperationChainRepositoryException(String message) {
        super(message);
    }

    public OperationChainRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
