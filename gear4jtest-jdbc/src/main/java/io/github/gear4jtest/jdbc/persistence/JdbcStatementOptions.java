package io.github.gear4jtest.jdbc.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable statement-level timeout policy shared by Gear4J JDBC repository
 * builders.
 */
public final class JdbcStatementOptions {
    private static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(30);

    private final int queryTimeoutSeconds;

    private JdbcStatementOptions(int queryTimeoutSeconds) {
        this.queryTimeoutSeconds = queryTimeoutSeconds;
    }

    public static JdbcStatementOptions defaults() {
        return of(DEFAULT_QUERY_TIMEOUT);
    }

    public static JdbcStatementOptions noTimeout() {
        return new JdbcStatementOptions(0);
    }

    public static JdbcStatementOptions of(Duration queryTimeout) {
        return new JdbcStatementOptions(toQueryTimeoutSeconds(queryTimeout));
    }

    public PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return configureNewStatement(connection.prepareStatement(sql));
    }

    /**
     * Configures a newly created statement, closing it if configuration fails. On
     * success, ownership is transferred to the caller. Use
     * {@link #apply(Statement)} when configuring a borrowed statement whose
     * lifetime belongs to someone else.
     */
    public <S extends Statement> S configureNewStatement(S statement) throws SQLException {
        Objects.requireNonNull(statement, "statement must not be null");
        try {
            apply(statement);
            return statement;
        } catch (SQLException | RuntimeException | Error failure) {
            try {
                statement.close();
            } catch (SQLException | RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) {
                    if (cleanupFailure instanceof Error fatal && !(failure instanceof Error)) {
                        fatal.addSuppressed(failure);
                        throw fatal;
                    }
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }

    public void apply(Statement statement) throws SQLException {
        Objects.requireNonNull(statement, "statement must not be null");
        statement.setQueryTimeout(queryTimeoutSeconds);
    }

    public int queryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    private static int toQueryTimeoutSeconds(Duration queryTimeout) {
        Objects.requireNonNull(queryTimeout, "queryTimeout must not be null");
        if (queryTimeout.isNegative()) {
            throw new IllegalArgumentException("queryTimeout must be >= 0");
        }
        if (queryTimeout.isZero()) {
            return 0;
        }
        long seconds = queryTimeout.getSeconds();
        if (queryTimeout.getNano() > 0) {
            if (seconds == Long.MAX_VALUE) {
                throw queryTimeoutTooLarge();
            }
            seconds++;
        }
        seconds = Math.max(1L, seconds);
        if (seconds > Integer.MAX_VALUE) {
            throw queryTimeoutTooLarge();
        }
        return (int) seconds;
    }

    private static IllegalArgumentException queryTimeoutTooLarge() {
        return new IllegalArgumentException("queryTimeout is too large for JDBC Statement#setQueryTimeout");
    }
}
