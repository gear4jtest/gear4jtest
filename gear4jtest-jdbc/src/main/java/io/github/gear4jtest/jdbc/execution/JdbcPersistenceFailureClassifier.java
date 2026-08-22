package io.github.gear4jtest.jdbc.execution;

import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
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
        return classifyFailure(failure).disposition();
    }

    RejectedPersistenceRecordContext rejectionContext(Exception failure) {
        Throwable diagnostic = classifyFailure(failure).diagnostic();
        if (diagnostic instanceof SQLException sqlException) {
            return new RejectedPersistenceRecordContext(sqlException.getClass().getName(),
                    sqlException.getSQLState(), sqlException.getErrorCode());
        }
        return new RejectedPersistenceRecordContext(diagnostic.getClass().getName(), null, null);
    }

    private Classification classifyFailure(Exception failure) {
        List<Throwable> failureChain = failureChain(failure);
        JsonProcessingException jsonFailure = null;
        SQLException firstSqlException = null;
        for (Throwable current : failureChain) {
            if (jsonFailure == null && current instanceof JsonProcessingException jsonProcessingException) {
                jsonFailure = jsonProcessingException;
            }
            if (firstSqlException == null && current instanceof SQLException sqlException) {
                firstSqlException = sqlException;
            }
        }
        if (jsonFailure != null) {
            return new Classification(PersistenceFailureDisposition.RECORD_REJECTED,
                    firstSqlException != null ? firstSqlException : jsonFailure);
        }

        Classification rejectedRecord = null;
        for (Throwable current : failureChain) {
            if (!(current instanceof SQLException sqlException)) {
                continue;
            }
            PersistenceFailureDisposition disposition = classifySqlException(sqlException);
            if (disposition == PersistenceFailureDisposition.RETRYABLE) {
                return new Classification(disposition, sqlException);
            }
            if (disposition == PersistenceFailureDisposition.RECORD_REJECTED && rejectedRecord == null) {
                rejectedRecord = new Classification(disposition, sqlException);
            }
        }
        if (rejectedRecord != null) {
            return rejectedRecord;
        }
        return new Classification(PersistenceFailureDisposition.SYSTEMIC,
                firstSqlException != null ? firstSqlException : failure);
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
                || state != null && REJECTED_RECORD_STATES.contains(state)
                || exception.getErrorCode() == 12899) {
            return PersistenceFailureDisposition.RECORD_REJECTED;
        }
        return PersistenceFailureDisposition.SYSTEMIC;
    }

    private static List<Throwable> failureChain(Throwable failure) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        List<Throwable> failures = new ArrayList<>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            failures.add(current);
            if (current.getCause() != null) {
                pending.addLast(current.getCause());
            }
            if (current instanceof SQLException sqlException && sqlException.getNextException() != null) {
                pending.addLast(sqlException.getNextException());
            }
        }
        return failures;
    }

    private record Classification(PersistenceFailureDisposition disposition, Throwable diagnostic) {}
}
