package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationChainTagRepositoryJdbcBehaviorTest {
    @Test
    void builder_shouldRejectMissingMandatoryValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainTagRepositoryJdbc.builder().build())
                .withMessage("ds must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainTagRepositoryJdbc.builder()
                        .dataSource(mock(DataSource.class))
                        .build())
                .withMessage("databaseDialect must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainTagRepositoryJdbc.builder()
                        .dataSource(mock(DataSource.class))
                        .databaseDialect(Gear4jDatabaseDialect.H2)
                        .statementOptions(null)
                        .build())
                .withMessage("statementOptions must not be null");
    }

    @Test
    void addTag_shouldBindValuesAndExecuteDialectSpecificUpsert() throws Exception {
        JdbcMocks jdbc = JdbcMocks.write();
        OperationChainTagRepositoryJdbc repository = repository(jdbc.dataSource());

        repository.addTag("pipeline", "stable");

        verify(jdbc.connection()).prepareStatement(contains("operation_chain_tag"));
        verify(jdbc.statement()).setString(1, "pipeline");
        verify(jdbc.statement()).setString(2, "stable");
        verify(jdbc.statement()).executeUpdate();
    }

    @Test
    void removeTag_shouldBindValuesAndExecuteDelete() throws Exception {
        JdbcMocks jdbc = JdbcMocks.write();
        OperationChainTagRepositoryJdbc repository = repository(jdbc.dataSource());

        repository.removeTag("pipeline", "stable");

        verify(jdbc.connection()).prepareStatement("DELETE FROM operation_chain_tag WHERE al_id=? AND tag=?");
        verify(jdbc.statement()).setString(1, "pipeline");
        verify(jdbc.statement()).setString(2, "stable");
        verify(jdbc.statement()).executeUpdate();
    }

    @Test
    void listTags_shouldReturnLinkedTagsAndBindPageWhenRequested() throws Exception {
        JdbcMocks jdbc = JdbcMocks.query("blue", "green");
        OperationChainTagRepositoryJdbc repository = repository(jdbc.dataSource());

        assertThat(repository.listTags("pipeline", new PageRequest(10, 20))).containsExactly("blue", "green");

        verify(jdbc.statement()).setString(1, "pipeline");
        verify(jdbc.statement()).setInt(2, 20);
        verify(jdbc.statement()).setInt(3, 10);
    }

    @Test
    void findAssemblyLineIdsByTag_shouldReturnOrderedIdsAndBindPageWhenRequested() throws Exception {
        JdbcMocks jdbc = JdbcMocks.query("a", "b");
        OperationChainTagRepositoryJdbc repository = repository(jdbc.dataSource());

        assertThat(repository.findAssemblyLineIdsByTag("stable", new PageRequest(5, 2))).containsExactly("a", "b");

        verify(jdbc.statement()).setString(1, "stable");
        verify(jdbc.statement()).setInt(2, 2);
        verify(jdbc.statement()).setInt(3, 5);
    }

    @Test
    void sqlFailures_shouldBeWrappedAsRepositoryExceptions() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("db down"));
        OperationChainTagRepositoryJdbc repository = repository(dataSource);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> repository.addTag("pipeline", "stable")))
                .isInstanceOf(OperationChainRepositoryException.class)
                .hasMessageContaining("add tag stable to pipeline")
                .hasCauseInstanceOf(java.sql.SQLException.class);
    }

    private static OperationChainTagRepositoryJdbc repository(DataSource dataSource) {
        return OperationChainTagRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
    }

    private record JdbcMocks(DataSource dataSource, Connection connection, PreparedStatement statement) {
        static JdbcMocks write() throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            return new JdbcMocks(dataSource, connection, statement);
        }

        static JdbcMocks query(String first, String second) throws Exception {
            JdbcMocks jdbc = write();
            ResultSet resultSet = mock(ResultSet.class);
            when(jdbc.statement().executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getString(1)).thenReturn(first, second);
            return jdbc;
        }
    }
}
