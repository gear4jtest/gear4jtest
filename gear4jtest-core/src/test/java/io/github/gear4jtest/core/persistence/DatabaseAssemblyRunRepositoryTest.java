package io.github.gear4jtest.core.persistence;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseAssemblyRunRepositoryTest {
    @Test
    void delete_shouldRestorePreviousAutoCommit() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement deleteLogs = mock(PreparedStatement.class);
        PreparedStatement deleteRun = mock(PreparedStatement.class);
        mockPostgresMetadata(connection);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.prepareStatement("DELETE FROM station_log WHERE pipeline_execution_id = ?"))
                .thenReturn(deleteLogs);
        when(connection.prepareStatement("DELETE FROM assembly_run WHERE id = ?")).thenReturn(deleteRun);

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource);

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
        mockPostgresMetadata(connection);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            return sql.startsWith("UPDATE station_log") ? update : insert;
        });
        when(update.executeUpdate()).thenReturn(0);
        SQLException duplicate = new SQLException("duplicate key", "23505");
        when(insert.executeUpdate()).thenThrow(duplicate);

        DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(dataSource, new ObjectMapper());
        StationLogRecord record = new StationLogRecord(UUID.randomUUID(), UUID.randomUUID(), "step", null,
                StationLogStatus.SUCCEEDED, Instant.now(), Instant.now(), null, null, Map.of(), "item-1");

        // When
        repository.saveOperationRecordsBatch(java.util.List.of(record));

        // Then
        verify(connection).commit();
        verify(connection).setAutoCommit(true);
    }

    @Test
    void initialize_shouldRejectUnsupportedDatabaseProvider() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        ResultSet tables = mock(ResultSet.class);
        when(metaData.getTables(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(tables);
        when(tables.next()).thenReturn(false);
        when(metaData.getDatabaseProductName()).thenReturn("UnknownDB");
        when(metaData.getDriverName()).thenReturn("Unknown Driver");
        when(metaData.getURL()).thenReturn("jdbc:unknown://localhost/test");

        // When / Then
        assertThatThrownBy(() -> new DatabaseAssemblyRunRepository(dataSource))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported Gear4J database provider");
    }

    @Test
    void jdbcDialect_shouldDetectMariaDbBeforeMySql() throws Exception {
        // Given
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");
        when(metaData.getDriverName()).thenReturn("MariaDB Connector/J");
        when(metaData.getURL()).thenReturn("jdbc:mariadb://localhost/gear4jtest");

        // When / Then
        assertThat(Gear4jJdbcDialect.from(metaData)).isEqualTo(Gear4jJdbcDialect.MARIADB);
    }

    private static void mockPostgresMetadata(Connection connection) throws Exception {
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
        when(metaData.getURL()).thenReturn("jdbc:postgresql://localhost/gear4jtest");
    }
}
