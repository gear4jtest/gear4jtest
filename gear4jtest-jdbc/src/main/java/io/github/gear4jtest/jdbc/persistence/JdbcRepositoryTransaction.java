package io.github.gear4jtest.jdbc.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

final class JdbcRepositoryTransaction {
    private JdbcRepositoryTransaction() {
    }

    static void run(DataSource dataSource, TransactionalWork work) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                work.execute(conn);
                conn.commit();
            } catch (SQLException e) {
                rollback(conn, e);
                throw e;
            } catch (RuntimeException e) {
                rollback(conn, e);
                throw e;
            } finally {
                conn.setAutoCommit(previousAutoCommit);
            }
        }
    }

    static void rollback(Connection conn, Exception original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    @FunctionalInterface
    interface TransactionalWork {
        void execute(Connection conn) throws SQLException;
    }
}
