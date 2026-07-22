package io.github.gear4jtest.external.api.repository;

import io.github.gear4jtest.external.api.exception.ExternalErrorCode;

/** Raised when a requested operation-chain repository update targets no row. */
public final class OperationChainNotFoundException extends OperationChainRepositoryException {
    public OperationChainNotFoundException(String message) {
        super(ExternalErrorCode.NOT_FOUND, message);
    }
}
