package io.github.gear4jtest.core.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.model.StationLogStatus;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseAssemblyRunRepositoryTest {

    @Test
    void save_shouldCommitAndRestorePreviousAutoCommit() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);
        AssemblyRunRecord run = runRecord();

        // When
        repository.save(run);

        // Then
        InOrder order = inOrder(connection, statement);
        order.verify(connection).setAutoCommit(false);
        order.verify(statement).executeUpdate();
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void update_shouldRollbackAndRestorePreviousAutoCommitOnSqlFailure() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException failure = new SQLException("boom");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenThrow(failure);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);
        AssemblyRunRecord run = runRecord();

        // When / Then
        assertThatThrownBy(() -> repository.update(run))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("Failed to update assembly run " + run.id());
        InOrder order = inOrder(connection, statement);
        order.verify(connection).setAutoCommit(false);
        order.verify(statement).executeUpdate();
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void update_shouldRollbackWhenNoAssemblyRunIsUpdated() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);
        AssemblyRunRecord run = runRecord();

        // When / Then
        assertThatThrownBy(() -> repository.update(run))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("Expected to update exactly one assembly run " + run.id())
                .hasMessageContaining("but updated 0 rows");
        InOrder order = inOrder(connection, statement);
        order.verify(connection).setAutoCommit(false);
        order.verify(statement).executeUpdate();
        order.verify(connection).rollback();
        order.verify(connection).setAutoCommit(true);
    }

    @Test
    void delete_shouldRestorePreviousAutoCommit() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement deleteLogs = mock(PreparedStatement.class);
        PreparedStatement deleteRun = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.prepareStatement("DELETE FROM station_log WHERE pipeline_execution_id = ?"))
                .thenReturn(deleteLogs);
        when(connection.prepareStatement("DELETE FROM assembly_run WHERE id = ?")).thenReturn(deleteRun);

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);

        // When
        repository.delete(UUID.randomUUID());

        // Then
        InOrder order = inOrder(connection, deleteLogs, deleteRun);
        order.verify(connection).setAutoCommit(false);
        order.verify(deleteLogs).executeUpdate();
        order.verify(deleteRun).executeUpdate();
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(false);
    }

    @Test
    void saveOperationRecordsBatch_shouldIgnoreDuplicateInsertForAlreadyFinalizedLog() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement update = mock(PreparedStatement.class);
        PreparedStatement insert = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            return sql.startsWith("UPDATE station_log") ? update : insert;
        });
        when(update.executeUpdate()).thenReturn(0);
        SQLException duplicate = new SQLException("duplicate key", "23505");
        when(insert.executeUpdate()).thenThrow(duplicate);

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL, new ObjectMapper());
        StationLogRecord record = new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "step", null,
                StationLogStatus.SUCCEEDED, Instant.now(), Instant.now(), null, null, Map.of(), "item-1");

        // When
        repository.saveOperationRecordsBatch(java.util.List.of(record));

        // Then
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void findById_shouldWrapSqlFailureWithRepositoryContext() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        SQLException failure = new SQLException("connection refused");
        UUID runId = UUID.randomUUID();
        when(dataSource.getConnection()).thenThrow(failure);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);

        // When / Then
        assertThatThrownBy(() -> repository.findById(runId))
                .isInstanceOf(ExecutionPersistenceException.class)
                .hasMessageContaining("Failed to find assembly run " + runId)
                .hasMessageContaining("PostgreSQL")
                .hasCause(failure);
    }

    @Test
    void constructor_shouldRequireAnExplicitDialect() {
        // Given
        DataSource dataSource = mock(DataSource.class);

        // When / Then
        assertThatThrownBy(() -> new DatabaseAssemblyRunRepository(dataSource, (Gear4jDatabaseDialect) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("databaseDialect must not be null");
    }

    @Test
    void constructor_shouldNotOpenAConnectionToDetectDialect() {
        // Given
        DataSource dataSource = mock(DataSource.class);

        // When
        new DatabaseAssemblyRunRepository(dataSource, Gear4jDatabaseDialect.MARIADB);

        // Then
        verifyNoInteractions(dataSource);
    }

    @Test
    void findAllPage_shouldBindPostgresqlLimitThenOffset() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);

        // When
        repository.findAll(new PageRequest(20, 10));

        // Then
        verify(statement).setInt(1, 10);
        verify(statement).setInt(2, 20);
    }

    @Test
    void findAllPage_shouldBindOracleOffsetThenFetchSize() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.ORACLE);

        // When
        repository.findAll(new PageRequest(20, 10));

        // Then
        verify(statement).setInt(1, 20);
        verify(statement).setInt(2, 10);
    }

    private static AssemblyRunRecord runRecord() {
        return new AssemblyRunRecord(UUID.randomUUID(), "checkout", Map.of("tenant", "demo"), Map.of("input", "x"),
                Map.of("result", "ok"), ExecutionStatus.SUCCEEDED, Instant.now(), Instant.now(), null, null, null,
                null);
    }

}
