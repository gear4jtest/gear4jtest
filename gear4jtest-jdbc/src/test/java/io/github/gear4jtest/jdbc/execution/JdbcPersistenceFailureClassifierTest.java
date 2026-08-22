package io.github.gear4jtest.jdbc.execution;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;

import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPersistenceFailureClassifierTest {
    private final JdbcPersistenceFailureClassifier classifier = new JdbcPersistenceFailureClassifier();

    @Test
    void classify_shouldUseRejectedRecordStateFromNextException() {
        // Given
        SQLException batchFailure = new SQLException("batch failed");
        SQLException recordFailure = new SQLException("value too long", "22001", 14_001);
        batchFailure.setNextException(recordFailure);
        ExecutionPersistenceException failure = new ExecutionPersistenceException("persistence failed", batchFailure);

        // When
        PersistenceFailureDisposition disposition = classifier.classify(failure);
        RejectedPersistenceRecordContext context = classifier.rejectionContext(failure);

        // Then
        assertThat(disposition).isEqualTo(PersistenceFailureDisposition.RECORD_REJECTED);
        assertThat(context.failureType()).isEqualTo(SQLException.class.getName());
        assertThat(context.sqlState()).isEqualTo("22001");
        assertThat(context.vendorCode()).isEqualTo(14_001);
    }

    @Test
    void classify_shouldPreferRetryableStateOverRejectedRecordStateInSqlChain() {
        // Given
        SQLException batchFailure = new SQLException("batch failed");
        SQLException recordFailure = new SQLException("value too long", "22001");
        SQLTimeoutException retryableFailure = new SQLTimeoutException("connection timed out", "08006");
        batchFailure.setNextException(recordFailure);
        recordFailure.setNextException(retryableFailure);

        // When
        PersistenceFailureDisposition disposition = classifier.classify(batchFailure);

        // Then
        assertThat(disposition).isEqualTo(PersistenceFailureDisposition.RETRYABLE);
    }

    @Test
    void classify_shouldTerminateWhenSqlNextExceptionChainContainsACycle() {
        // Given
        CyclicSQLException first = new CyclicSQLException("first", null);
        CyclicSQLException rejected = new CyclicSQLException("value too long", "22001");
        first.next(rejected);
        rejected.next(first);

        // When
        PersistenceFailureDisposition disposition = classifier.classify(first);

        // Then
        assertThat(disposition).isEqualTo(PersistenceFailureDisposition.RECORD_REJECTED);
    }

    private static final class CyclicSQLException extends SQLException {
        private static final long serialVersionUID = 1L;

        private SQLException next;

        private CyclicSQLException(String reason, String sqlState) {
            super(reason, sqlState);
        }

        @Override
        public SQLException getNextException() {
            return next;
        }

        private void next(SQLException next) {
            this.next = next;
        }
    }
}
