package io.github.gear4jtest.external.api.exception;

import io.github.gear4jtest.external.api.repository.OperationChainNotFoundException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalErrorTaxonomyTest {
    @Test
    void publicFailures_shouldExposeStableErrorCodes() {
        assertThat(new CompilationException("invalid source").errorCode())
                .isEqualTo(ExternalErrorCode.COMPILATION);
        assertThat(new OperationChainNotFoundException("missing").errorCode())
                .isEqualTo(ExternalErrorCode.NOT_FOUND);
        assertThat(new OperationChainPublicationConflictException("conflict").errorCode())
                .isEqualTo(ExternalErrorCode.CONFLICT);
        assertThat(new OperationChainRepositoryException("database unavailable").errorCode())
                .isEqualTo(ExternalErrorCode.STORAGE_UNAVAILABLE);
        assertThat(new PolicyViolationException("forbidden").errorCode())
                .isEqualTo(ExternalErrorCode.VALIDATION);
        assertThat(new ExternalValidationException("invalid request").errorCode())
                .isEqualTo(ExternalErrorCode.VALIDATION);
    }
}
