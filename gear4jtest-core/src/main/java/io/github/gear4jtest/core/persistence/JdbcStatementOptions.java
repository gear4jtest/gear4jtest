package io.github.gear4jtest.core.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;

import io.github.gear4jtest.core.api.annotation.Internal;

/** JDBC statement-level safety options shared by Gear4J JDBC repositories. */
@Internal
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
        PreparedStatement statement = connection.prepareStatement(sql);
        apply(statement);
        return statement;
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
        long millis = queryTimeout.toMillis();
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        if (seconds > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("queryTimeout is too large for JDBC Statement#setQueryTimeout");
        }
        return (int) seconds;
    }
}
