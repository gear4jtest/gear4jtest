package io.github.gear4jtest.external.api.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationChainConfigRepositoryJdbcBehaviorTest {
    @Test
    void builder_shouldRejectMissingMandatoryValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainConfigRepositoryJdbc.builder().build())
                .withMessage("ds must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainConfigRepositoryJdbc.builder()
                        .dataSource(mock(DataSource.class))
                        .build())
                .withMessage("databaseDialect must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainConfigRepositoryJdbc.builder()
                        .dataSource(mock(DataSource.class))
                        .databaseDialect(Gear4jDatabaseDialect.H2)
                        .objectMapper(null)
                        .build())
                .withMessage("objectMapper must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainConfigRepositoryJdbc.builder()
                        .dataSource(mock(DataSource.class))
                        .databaseDialect(Gear4jDatabaseDialect.H2)
                        .statementOptions(null)
                        .build())
                .withMessage("statementOptions must not be null");
    }

    @Test
    void upsert_shouldBindConfigurationAsDialectAwareValues() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.write();
        OperationChainConfigRepositoryJdbc repository = repository(jdbc.dataSource());

        // When
        repository.upsert(new OperationChainConfig("pipeline", true, StoreType.FILESYSTEM,
                Map.of("root", "/tmp/store")));

        // Then
        verify(jdbc.statement()).setString(1, "pipeline");
        verify(jdbc.statement()).setBoolean(2, true);
        verify(jdbc.statement()).setString(3, "FILESYSTEM");
        verify(jdbc.statement()).setString(4, "{\"root\":\"/tmp/store\"}");
        verify(jdbc.statement()).executeUpdate();
    }

    @Test
    void setAllowRunPublicationWithoutTest_shouldBindFlagAndIdentifier() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.write();
        OperationChainConfigRepositoryJdbc repository = repository(jdbc.dataSource());

        // When
        repository.setAllowRunPublicationWithoutTest("pipeline", false);

        // Then
        verify(jdbc.statement()).setBoolean(1, false);
        verify(jdbc.statement()).setString(2, "pipeline");
        verify(jdbc.statement()).executeUpdate();
    }

    @Test
    void updateStore_shouldRejectNullTypeAndBindJsonProperties() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.write();
        OperationChainConfigRepositoryJdbc repository = repository(jdbc.dataSource());

        // When / Then
        assertThatNullPointerException()
                .isThrownBy(() -> repository.updateStore("pipeline", null, Map.of()))
                .withMessage("storeType must not be null");

        repository.updateStore("pipeline", StoreType.MEMORY, Map.of("name", "primary"));

        verify(jdbc.statement()).setString(1, "MEMORY");
        verify(jdbc.statement()).setString(2, "{\"name\":\"primary\"}");
        verify(jdbc.statement()).setString(3, "pipeline");
        verify(jdbc.statement()).executeUpdate();
    }

    @Test
    void findByAssemblyLineId_shouldReturnEmptyWhenNoRowExists() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.query(false);
        OperationChainConfigRepositoryJdbc repository = repository(jdbc.dataSource());

        // When / Then
        assertThat(repository.findByAssemblyLineId("missing")).isEmpty();
        verify(jdbc.statement()).setString(1, "missing");
    }

    @Test
    void findByAssemblyLineId_shouldRejectInvalidJsonMaps() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.query(true);
        when(jdbc.resultSet().getString(1)).thenReturn("pipeline");
        when(jdbc.resultSet().getBoolean(2)).thenReturn(true);
        when(jdbc.resultSet().getString(3)).thenReturn("MEMORY");
        when(jdbc.resultSet().getString(4)).thenReturn("not-json");
        OperationChainConfigRepositoryJdbc repository = repository(jdbc.dataSource());

        // When / Then
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> repository.findByAssemblyLineId("pipeline")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JSON map");
    }

    @Test
    void sqlFailures_shouldBeWrappedAsRuntimeExceptions() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        SQLException failure = new SQLException("db down");
        when(dataSource.getConnection()).thenThrow(failure);
        OperationChainConfigRepositoryJdbc repository = repository(dataSource);

        // When / Then
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> repository.findByAssemblyLineId("pipeline")))
                .isInstanceOf(RuntimeException.class)
                .hasCause(failure);
    }

    private static OperationChainConfigRepositoryJdbc repository(DataSource dataSource) {
        return OperationChainConfigRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
    }

    private record JdbcMocks(DataSource dataSource,
                             Connection connection,
                             PreparedStatement statement,
                             ResultSet resultSet) {
        static JdbcMocks write() throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            return new JdbcMocks(dataSource, connection, statement, null);
        }

        static JdbcMocks query(boolean rowExists) throws Exception {
            JdbcMocks jdbc = write();
            ResultSet resultSet = mock(ResultSet.class);
            when(jdbc.statement().executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(rowExists);
            return new JdbcMocks(jdbc.dataSource(), jdbc.connection(), jdbc.statement(), resultSet);
        }
    }
}
