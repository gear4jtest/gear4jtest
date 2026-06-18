package io.github.gear4jtest.external.api.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationChainObjectRepositoryJdbcTest {
    @Test
    void findAll_shouldApplySqlLevelPaginationWhenPageRequestIsProvided() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        String[] preparedSql = new String[1];
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            preparedSql[0] = invocation.getArgument(0);
            return statement;
        });
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        OperationChainObjectRepositoryJdbc repository = OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();

        // When
        var result = repository.findAll("pipeline", new PageRequest(5, 10));

        // Then
        assertThat(result).isEmpty();
        assertThat(preparedSql[0]).contains("LIMIT ? OFFSET ?");
        verify(statement).setString(1, "pipeline");
        verify(statement).setInt(2, 10);
        verify(statement).setInt(3, 5);
    }

    @Test
    void find_shouldMapNullableTimestampsAsNull() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getLong("id")).thenReturn(42L);
        when(resultSet.getString("al_id")).thenReturn("pipeline");
        when(resultSet.getString("version")).thenReturn("1.0.0");
        when(resultSet.getString("mode")).thenReturn("RUN");
        when(resultSet.getString("content_hash")).thenReturn("a".repeat(64));
        when(resultSet.getLong("size_bytes")).thenReturn(123L);
        when(resultSet.getString("mime_type")).thenReturn("application/xml");
        when(resultSet.getTimestamp("created_at")).thenReturn(null);
        when(resultSet.getTimestamp("published_at")).thenReturn(null);

        OperationChainObjectRepositoryJdbc repository = OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
                .build();

        // When
        var result = repository.find("pipeline", "1.0.0", ExecutionMode.RUN).orElseThrow();

        // Then
        assertThat(result.createdAt()).isNull();
        assertThat(result.publishedAt()).isNull();
    }
}
