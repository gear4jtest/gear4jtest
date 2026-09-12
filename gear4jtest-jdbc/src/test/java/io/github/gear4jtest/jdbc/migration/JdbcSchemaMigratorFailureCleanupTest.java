package io.github.gear4jtest.jdbc.migration;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcSchemaMigratorFailureCleanupTest {
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void fatalFailure_shouldRollbackBeforeRestoringAutoCommit(boolean retry) throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        var fatal = new AssertionError("metadata failed");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenThrow(fatal);

        // When / Then
        assertThatThrownBy(() -> invoke(connection, retry)).isSameAs(fatal);
        var order = inOrder(connection);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedRollback_shouldRetainCauseAndNeverEnableAutoCommit(boolean retry) throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        var fatal = new AssertionError("migration failed");
        var rollbackFailure = new SQLException("rollback failed");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenThrow(fatal);
        doThrow(rollbackFailure).when(connection).rollback();

        // When / Then
        assertThatThrownBy(() -> invoke(connection, retry)).isSameAs(fatal);
        assertThat(fatal.getSuppressed()).containsExactly(rollbackFailure);
        verify(connection, never()).setAutoCommit(true);
        verify(connection, never()).commit();
        verify(connection, never()).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failedRestoration_shouldRetainTheOriginalFailure(boolean retry) throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        var original = new SQLException("migration failed");
        var restoreFailure = new SQLException("restoration failed");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenThrow(original);
        doThrow(restoreFailure).when(connection).setAutoCommit(true);

        // When / Then
        assertThatThrownBy(() -> invoke(connection, retry)).isInstanceOf(SchemaMigrationException.class)
                .hasCause(original);
        assertThat(original.getSuppressed()).containsExactly(restoreFailure);
        verify(connection).rollback();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void fatalFailure_shouldLeaveCallerTransactionUntouched(boolean retry) throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        var fatal = new AssertionError("caller transaction failure");
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.getMetaData()).thenThrow(fatal);

        // When / Then
        assertThatThrownBy(() -> invoke(connection, retry)).isSameAs(fatal);
        verify(connection, never()).rollback();
        verify(connection, never()).commit();
        verify(connection, never()).setAutoCommit(true);
        verify(connection, never()).setAutoCommit(false);
        verify(connection, never()).close();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void fatalRestoration_shouldEscapeWithTheOriginalSqlFailure(boolean retry) throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        var original = new SQLException("migration failed");
        var fatal = new AssertionError("fatal restoration");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenThrow(original);
        doThrow(fatal).when(connection).setAutoCommit(true);

        // When / Then
        assertThatThrownBy(() -> invoke(connection, retry)).isSameAs(fatal);
        assertThat(fatal.getSuppressed()).containsExactly(original);
    }

    @Test
    void dataSourceVariant_shouldCloseItsConnectionWhenMigrationFailsFatally() throws Exception {
        // Given
        Connection connection = mock(Connection.class);
        DataSource dataSource = mock(DataSource.class);
        var fatal = new AssertionError("migration failed");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getMetaData()).thenThrow(fatal);

        // When / Then
        assertThatThrownBy(() -> migrator().migrate(dataSource)).isSameAs(fatal);
        verify(connection).rollback();
        verify(connection).close();
    }

    private static void invoke(Connection connection, boolean retry) {
        if (retry) {
            migrator().prepareRetry(connection, "1");
        } else {
            migrator().migrate(connection);
        }
    }

    private static JdbcSchemaMigrator migrator() {
        return JdbcSchemaMigrator.builder().moduleId("failure-cleanup")
                .dialect(Gear4jDatabaseDialect.POSTGRESQL).baselineTableName("probe")
                .migrationListResource("unused/migrations.list").build();
    }
}
