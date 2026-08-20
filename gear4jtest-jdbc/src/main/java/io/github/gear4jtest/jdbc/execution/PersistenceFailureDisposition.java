package io.github.gear4jtest.jdbc.execution;

enum PersistenceFailureDisposition {
    RETRYABLE,
    RECORD_REJECTED,
    SYSTEMIC
}
