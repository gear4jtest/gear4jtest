package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import io.test.gear4jtest.external.api.StoreType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationChainConfigRepositoryJdbcTest {
    @Test
    void findByAssemblyLineId_shouldParseJsonStorePropsWithCommaInValue() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("pipeline");
        when(resultSet.getBoolean(2)).thenReturn(true);
        when(resultSet.getString(3)).thenReturn("FILESYSTEM");
        when(resultSet.getString(4)).thenReturn("{\"path\":\"hello, world\",\"quoted\":\"a\\\"b\"}");

        OperationChainConfigRepositoryJdbc repository = new OperationChainConfigRepositoryJdbc(dataSource,
                JdbcDialect.POSTGRES);

        // When
        var result = repository.findByAssemblyLineId("pipeline").orElseThrow();

        // Then
        assertThat(result.storeType()).isEqualTo(StoreType.FILESYSTEM);
        assertThat(result.storeProps()).containsEntry("path", "hello, world")
                .containsEntry("quoted", "a\"b");
    }
}
