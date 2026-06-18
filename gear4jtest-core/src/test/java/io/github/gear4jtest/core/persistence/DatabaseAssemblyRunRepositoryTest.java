package io.github.gear4jtest.core.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.exception.ExecutionPersistenceException;
import io.github.gear4jtest.core.model.StationLogStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
    void save_shouldApplyDefaultJdbcStatementTimeout() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL);

        // When
        repository.save(runRecord());

        // Then
        verify(statement).setQueryTimeout(30);
    }

    @Test
    void save_shouldAllowDisablingJdbcStatementTimeout() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL, new ObjectMapper(), Duration.ZERO);

        // When
        repository.save(runRecord());

        // Then
        verify(statement).setQueryTimeout(0);
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
    void saveOperationRecordsBatch_shouldUseJdbcBatchForOpenUpdatesAndMissingInserts() throws Exception {
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
        when(update.executeBatch()).thenReturn(new int[] { 1, 0 });
        when(insert.executeBatch()).thenReturn(new int[] { 1 });

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.H2, new ObjectMapper());
        StationLogRecord first = stationLogRecord("step-1");
        StationLogRecord second = stationLogRecord("step-2");

        // When
        repository.saveOperationRecordsBatch(java.util.List.of(first, second));

        // Then
        verify(update, times(2)).addBatch();
        verify(update).executeBatch();
        verify(insert).addBatch();
        verify(insert).executeBatch();
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void recordsRequiringInsert_shouldTreatSuccessNoInfoAsSuccessfulUpdate() {
        // Given
        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(mock(DataSource.class),
                Gear4jDatabaseDialect.H2, new ObjectMapper());
        StationLogRecord first = stationLogRecord("step-1");
        StationLogRecord second = stationLogRecord("step-2");

        // When
        var insertCandidates = repository.recordsRequiringInsert(java.util.List.of(first, second),
                                                                 new int[] { Statement.SUCCESS_NO_INFO, 0 });

        // Then
        assertThat(insertCandidates).containsExactly(second);
    }

    @Test
    void saveOperationRecordsBatch_shouldUseNativeUpsertForSupportedDialects() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement upsert = mock(PreparedStatement.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(sqlCaptor.capture())).thenReturn(upsert);
        when(upsert.executeBatch()).thenReturn(new int[] { 1, 1 });

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.POSTGRESQL, new ObjectMapper());
        StationLogRecord first = stationLogRecord("step-1");
        StationLogRecord second = stationLogRecord("step-2");

        // When
        repository.saveOperationRecordsBatch(java.util.List.of(first, second));

        // Then
        assertThat(sqlCaptor.getValue())
                .contains("ON CONFLICT (id) DO UPDATE")
                .contains("WHERE station_log.end_time IS NULL");
        verify(upsert, times(2)).addBatch();
        verify(upsert).executeBatch();
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void saveOperationRecordsBatch_shouldFallbackToSingleRecordAlgorithmOnDuplicateBatchInsert() throws Exception {
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
        when(update.executeBatch()).thenReturn(new int[] { 0 });
        SQLException duplicate = new SQLException("duplicate key", "23505");
        when(insert.executeBatch()).thenThrow(duplicate);
        when(update.executeUpdate()).thenReturn(0);

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource,
                Gear4jDatabaseDialect.H2, new ObjectMapper());
        StationLogRecord record = stationLogRecord("step");

        // When
        repository.saveOperationRecordsBatch(java.util.List.of(record));

        // Then
        verify(update).executeBatch();
        verify(insert).executeBatch();
        verify(update).executeUpdate();
        verify(insert).executeUpdate();
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

    private static StationLogRecord stationLogRecord(String operationId) {
        return new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), operationId, null, StationLogStatus.SUCCEEDED,
                Instant.now(), Instant.now(), null, null, Map.of(), "item-1");
    }

    private static AssemblyRunRecord runRecord() {
        return new AssemblyRunRecord(UUID.randomUUID(), "checkout", Map.of("tenant", "demo"), Map.of("input", "x"),
                Map.of("result", "ok"), ExecutionStatus.SUCCEEDED, Instant.now(), Instant.now(), null, null, null,
                null);
    }

}
