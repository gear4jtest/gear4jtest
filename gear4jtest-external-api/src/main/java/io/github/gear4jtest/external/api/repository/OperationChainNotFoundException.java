package io.github.gear4jtest.external.api.repository;

/** Raised when a requested operation-chain repository update targets no row. */
public final class OperationChainNotFoundException extends OperationChainRepositoryException {
    public OperationChainNotFoundException(String message) {
        super(message);
    }
}
