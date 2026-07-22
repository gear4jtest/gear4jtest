package io.github.gear4jtest.external.api.repository;

import io.github.gear4jtest.external.api.exception.ExternalErrorCode;
import io.github.gear4jtest.external.api.exception.Gear4jExternalException;

/** Base exception for failures raised by operation-chain repositories. */
public class OperationChainRepositoryException extends Gear4jExternalException {
    public OperationChainRepositoryException(String message) {
        this(ExternalErrorCode.STORAGE_UNAVAILABLE, message, null);
    }

    public OperationChainRepositoryException(String message, Throwable cause) {
        this(ExternalErrorCode.STORAGE_UNAVAILABLE, message, cause);
    }

    protected OperationChainRepositoryException(ExternalErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    protected OperationChainRepositoryException(ExternalErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
