package io.github.gear4jtest.jdbc.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MigrationLockStoreTest {
    @Test
    void missingLockRowMustNotAllowMigrationToProceed() throws Exception {
        // Given
        var connection = mock(Connection.class);
        var select = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(mock(ResultSet.class));
        // When / Then
        assertThatThrownBy(() -> lock(mock(BaselineSchemaValidator.class)).acquire(connection))
                .isInstanceOf(SchemaMigrationException.class).hasMessageContaining("lock row is missing");
        verify(select, never()).executeUpdate();
    }

    @Test
    void concurrentInfrastructureCreationStillInitializesTheLockRow() throws Exception {
        // Given
        var schema = mock(BaselineSchemaValidator.class);
        var connection = mock(Connection.class);
        var create = mock(Statement.class);
        var insert = mock(PreparedStatement.class);
        when(schema.tableExists(connection, "gear4j_schema_lock")).thenReturn(false, true);
        when(connection.createStatement()).thenReturn(create);
        when(create.execute(anyString())).thenThrow(new SQLException("already exists"));
        when(connection.prepareStatement(anyString())).thenReturn(insert);
        // When
        lock(schema).ensureTableAndRow(connection);
        // Then
        verify(insert).setString(1, "module");
        verify(insert).executeUpdate();
        verify(create).close();
    }

    @Test
    void failedInfrastructureCreationIsNotMistakenForAConcurrentSuccess() throws Exception {
        // Given
        var connection = mock(Connection.class);
        var create = mock(Statement.class);
        var failure = new SQLException("permission denied", "42501");
        when(connection.createStatement()).thenReturn(create);
        when(create.execute(anyString())).thenThrow(failure);
        // When / Then
        assertThatThrownBy(() -> lock(mock(BaselineSchemaValidator.class)).ensureTableAndRow(connection))
                .isSameAs(failure);
        verify(connection, never()).prepareStatement(anyString());
        verify(create).close();
    }

    private static MigrationLockStore lock(BaselineSchemaValidator schema) {
        return new MigrationLockStore("module", Gear4jDatabaseDialect.H2, schema, JdbcStatementOptions.defaults());
    }
}
