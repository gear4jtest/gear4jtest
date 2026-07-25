package io.github.gear4jtest.jdbc.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;

/**
 * Owns the transaction boundary used by a JDBC repository write.
 * <p>
 * Repository code supplies only the SQL work. The implementation decides how
 * the connection is acquired and whether commit/rollback is performed directly
 * or delegated to a framework transaction manager.
 */
@FunctionalInterface
public interface JdbcTransactionOperations {

    /**
     * Executes the work synchronously inside the configured transaction boundary.
     *
     * @param work work to execute with the transaction-bound connection
     * @throws SQLException when connection management, SQL work or transaction
     *                      completion fails
     */
    void execute(Work work) throws SQLException;

    /**
     * Executes synchronous work that returns a value inside the same boundary.
     */
    default <T> T executeReturning(ReturningWork<T> work) throws SQLException {
        Objects.requireNonNull(work, "work must not be null");
        AtomicReference<T> result = new AtomicReference<>();
        execute(connection -> result.set(work.execute(connection)));
        return result.get();
    }

    /**
     * Creates library-owned autonomous transaction operations.
     * <p>
     * Each call acquires and closes one connection, commits on success and rolls
     * back on failure. The acquired connection must initially be in auto-commit
     * mode. A connection already participating in an ambient transaction is
     * rejected before the callback runs, preventing Gear4J from committing or
     * rolling back caller-owned work.
     */
    static JdbcTransactionOperations autonomous(DataSource dataSource) {
        DataSource requiredDataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        String ambientTransactionMessage = "Autonomous JDBC transactions require a fresh connection with "
                + "autoCommit=true; the acquired connection is already transaction-bound. Configure "
                + "JdbcTransactionOperations explicitly instead of allowing Gear4J to own this transaction.";
        return work -> {
            Objects.requireNonNull(work, "work must not be null");
            try (Connection connection = requiredDataSource.getConnection()) {
                if (!connection.getAutoCommit()) {
                    throw new SQLException(ambientTransactionMessage);
                }
                connection.setAutoCommit(false);
                Throwable failure = null;
                try {
                    work.execute(connection);
                    connection.commit();
                } catch (SQLException | RuntimeException | Error exception) {
                    failure = exception;
                    rollback(connection, exception);
                    throw exception;
                } finally {
                    restoreAutoCommit(connection, failure);
                }
            }
        };
    }

    /** SQL work executed inside a transaction boundary. */
    @FunctionalInterface
    interface Work {
        void execute(Connection connection) throws SQLException;
    }

    /** SQL work that returns a value from a transaction boundary. */
    @FunctionalInterface
    interface ReturningWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, Throwable original) throws SQLException {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException restoreFailure) {
            if (original == null) {
                throw restoreFailure;
            }
            original.addSuppressed(restoreFailure);
        }
    }
}
