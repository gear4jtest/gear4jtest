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
        when(connection.prepareStatement(
                                         "SELECT * FROM assembly_run ORDER BY start_time DESC LIMIT ? OFFSET ?"))
                .thenReturn(statement);
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
        when(connection.prepareStatement(
                                         "SELECT * FROM assembly_run ORDER BY start_time DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"))
                .thenReturn(statement);
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

}
