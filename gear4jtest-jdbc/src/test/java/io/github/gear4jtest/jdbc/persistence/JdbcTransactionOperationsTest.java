package io.github.gear4jtest.jdbc.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcTransactionOperationsTest {

    @Test
    void autonomous_shouldOwnCommitAndConnectionLifecycle() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        JdbcTransactionOperations transactions = JdbcTransactionOperations.autonomous(dataSource);

        // When
        String result = transactions.executeReturning(ignored -> "saved");

        // Then
        assertThat(result).isEqualTo("saved");
        InOrder order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
        order.verify(connection).close();
    }

    @Test
    void autonomous_shouldRollbackAndPreserveCleanupFailures() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        SQLException workFailure = new SQLException("write failed");
        SQLException rollbackFailure = new SQLException("rollback failed");
        SQLException restoreFailure = new SQLException("restore failed");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        org.mockito.Mockito.doThrow(rollbackFailure).when(connection).rollback();
        org.mockito.Mockito.doThrow(restoreFailure).when(connection).setAutoCommit(true);
        JdbcTransactionOperations transactions = JdbcTransactionOperations.autonomous(dataSource);

        // When / Then
        assertThatThrownBy(() -> transactions.execute(ignored -> {
            throw workFailure;
        }))
                .isSameAs(workFailure);
        assertThat(workFailure.getSuppressed()).containsExactly(rollbackFailure, restoreFailure);
        verify(connection, never()).commit();
        verify(connection).close();
    }

    @Test
    void autonomous_shouldRejectConnectionAlreadyBoundToAmbientTransaction() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        AtomicBoolean callbackExecuted = new AtomicBoolean();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        JdbcTransactionOperations transactions = JdbcTransactionOperations.autonomous(dataSource);

        // When / Then
        assertThatThrownBy(() -> transactions.execute(ignored -> {
            callbackExecuted.set(true);
        }))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("already transaction-bound");
        assertThat(callbackExecuted).isFalse();
        verify(connection, never()).setAutoCommit(false);
        verify(connection, never()).commit();
        verify(connection, never()).rollback();
        verify(connection).close();
    }
}
