package io.github.gear4jtest.jdbc.execution;

import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Classifies persistence failures without treating unknown constraint errors as
 * poison records.
 */
final class JdbcPersistenceFailureClassifier {
    private static final Set<String> RETRYABLE_STATES = Set.of("40001", "40P01");
    private static final Set<String> REJECTED_RECORD_STATES = Set.of(
                                                                     "22001", "22003", "22007", "22008", "22018",
                                                                     "22019", "22021");

    PersistenceFailureDisposition classify(Exception failure) {
        if (findCause(failure, JsonProcessingException.class) != null) {
            return PersistenceFailureDisposition.RECORD_REJECTED;
        }
        SQLException sqlException = findCause(failure, SQLException.class);
        if (sqlException == null) {
            return PersistenceFailureDisposition.SYSTEMIC;
        }
        return classifySqlException(sqlException);
    }

    RejectedPersistenceRecordContext rejectionContext(Exception failure) {
        SQLException sqlException = findCause(failure, SQLException.class);
        if (sqlException != null) {
            return new RejectedPersistenceRecordContext(sqlException.getClass().getName(),
                    sqlException.getSQLState(), sqlException.getErrorCode());
        }
        Throwable recordFailure = findCause(failure, JsonProcessingException.class);
        Throwable diagnostic = recordFailure != null ? recordFailure : failure;
        return new RejectedPersistenceRecordContext(diagnostic.getClass().getName(), null, null);
    }

    private PersistenceFailureDisposition classifySqlException(SQLException exception) {
        String state = exception.getSQLState();
        if (exception instanceof SQLTimeoutException
                || exception instanceof SQLTransientException
                || exception instanceof SQLRecoverableException
                || state != null && (state.startsWith("08") || RETRYABLE_STATES.contains(state))) {
            return PersistenceFailureDisposition.RETRYABLE;
        }
        if (exception instanceof SQLDataException
                || REJECTED_RECORD_STATES.contains(state)
                || exception.getErrorCode() == 12899) {
            return PersistenceFailureDisposition.RECORD_REJECTED;
        }
        return PersistenceFailureDisposition.SYSTEMIC;
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
