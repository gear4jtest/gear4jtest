package io.github.gear4jtest.studio.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Function;
import javax.sql.DataSource;

import io.github.gear4jtest.studio.management.StudioStore;
import io.github.gear4jtest.studio.service.StudioProblem;

/**
 * Owns each borrowed connection and transaction; never closes the host
 * DataSource.
 */
public final class JdbcStudioStore implements StudioStore {
    private final DataSource dataSource;

    public JdbcStudioStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    @Override
    public void bindContext(String runtimeContextId) {
        if (runtimeContextId == null || runtimeContextId.isBlank() || runtimeContextId.length() > 255)
            throw new IllegalArgumentException("Invalid runtime context identity");
        transaction(tx -> {
            ((JdbcStudioSession) tx).bindContext(runtimeContextId);
            return null;
        }, false);
    }

    @Override
    public <T> T read(Function<Session, T> work) {
        return transaction(work, true);
    }

    @Override
    public <T> T write(Function<Session, T> work) {
        return transaction(work, false);
    }

    private <T> T transaction(Function<Session, T> work, boolean readOnly) {
        try (var connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit())
                throw new IllegalStateException("Studio requires a fresh transaction boundary");
            connection.setTransactionIsolation(readOnly ? Connection.TRANSACTION_REPEATABLE_READ
                    : Connection.TRANSACTION_READ_COMMITTED);
            connection.setReadOnly(readOnly);
            connection.setAutoCommit(false);
            var session = new JdbcStudioSession(connection, readOnly);
            try {
                T result = work.apply(session);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            } finally {
                session.expire();
            }
        } catch (SQLException failure) {
            throw problem(failure);
        }
    }

    static StudioProblem problem(SQLException failure) {
        String state = failure.getSQLState();
        var problem = new StudioProblem(state != null && (state.equals("23505") || state.startsWith("40"))
                ? StudioProblem.Code.CONFLICT : StudioProblem.Code.STORAGE_UNAVAILABLE);
        problem.initCause(failure);
        return problem;
    }
}
