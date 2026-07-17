package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationChainObjectRepositoryJdbcBehaviorTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void builder_shouldRejectMissingMandatoryValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainObjectRepositoryJdbc.builder().build())
                .withMessage("ds must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainObjectRepositoryJdbc.builder()
                        .dataSource(mock(DataSource.class))
                        .build())
                .withMessage("databaseDialect must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> OperationChainObjectRepositoryJdbc.builder()
                        .dataSource(mock(DataSource.class))
                        .databaseDialect(Gear4jDatabaseDialect.H2)
                        .statementOptions(null)
                        .build())
                .withMessage("statementOptions must not be null");
    }

    @Test
    void insert_shouldBindObjectValuesAndReturnGeneratedId() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.writeWithGeneratedKey(99L);
        OperationChainObjectRepositoryJdbc repository = repository(jdbc.dataSource());
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant publishedAt = Instant.parse("2026-01-01T01:00:00Z");

        // When
        long id = repository.insert(new OperationChainObject(null, "pipeline", "1.0.0", ExecutionMode.TEST,
                HASH.toUpperCase(), 42L, "application/xml", createdAt, "tester", publishedAt));

        // Then
        assertThat(id).isEqualTo(99L);
        verify(jdbc.connection()).prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS));
        verify(jdbc.statement()).setString(1, "pipeline");
        verify(jdbc.statement()).setString(2, "1.0.0");
        verify(jdbc.statement()).setString(3, "TEST");
        verify(jdbc.statement()).setString(4, HASH);
        verify(jdbc.statement()).setLong(5, 42L);
        verify(jdbc.statement()).setString(6, "application/xml");
        verify(jdbc.statement()).setTimestamp(eq(7), eq(Timestamp.from(createdAt)), any(Calendar.class));
        verify(jdbc.statement()).setString(8, "tester");
        verify(jdbc.statement()).setTimestamp(eq(9), eq(Timestamp.from(publishedAt)), any(Calendar.class));
    }

    @Test
    void insert_shouldRejectInvalidContentHashBeforeWriting() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.writeWithGeneratedKey(99L);
        OperationChainObjectRepositoryJdbc repository = repository(jdbc.dataSource());

        // When / Then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> repository.insert(new OperationChainObject(null, "pipeline", "1.0.0",
                        ExecutionMode.TEST, "bad", 42L, "application/xml", Instant.EPOCH, "tester",
                        Instant.EPOCH)))
                .withMessageContaining("SHA-256");
    }

    @Test
    void find_shouldReturnEmptyWhenNoRowExistsAndLowercaseContentHashWhenFound() throws Exception {
        // Given
        JdbcMocks missing = JdbcMocks.query(false);
        JdbcMocks existing = JdbcMocks.query(true);
        stubObjectRow(existing.resultSet(), 42L, HASH.toUpperCase(),
                      Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
                      Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")));

        // When / Then
        assertThat(repository(missing.dataSource()).find("pipeline", "1", ExecutionMode.RUN)).isEmpty();
        OperationChainObject object = repository(existing.dataSource()).find("pipeline", "1", ExecutionMode.RUN)
                .orElseThrow();

        assertThat(object.id()).isEqualTo(42L);
        assertThat(object.contentHash()).isEqualTo(HASH);
        assertThat(object.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(object.publishedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
    }

    @Test
    void findLatestRun_shouldLimitRowsAndReturnLatestRun() throws Exception {
        // Given
        JdbcMocks jdbc = JdbcMocks.query(true);
        Instant createdAt = Instant.parse("2026-02-01T00:00:00Z");
        Instant publishedAt = Instant.parse("2026-02-02T00:00:00Z");
        stubObjectRow(jdbc.resultSet(), 7L, HASH, Timestamp.from(createdAt), Timestamp.from(publishedAt));
        OperationChainObjectRepositoryJdbc repository = repository(jdbc.dataSource());

        // When
        OperationChainObject result = repository.findLatestRun("pipeline").orElseThrow();

        // Then
        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.publishedAt()).isEqualTo(publishedAt);
        verify(jdbc.statement()).setString(1, "pipeline");
        verify(jdbc.statement()).setMaxRows(1);
    }

    @Test
    void exists_shouldReturnWhetherARowExists() throws Exception {
        // Given
        JdbcMocks existing = JdbcMocks.query(true);
        JdbcMocks missing = JdbcMocks.query(false);

        // When / Then
        assertThat(repository(existing.dataSource()).exists("pipeline", "1", ExecutionMode.RUN)).isTrue();
        assertThat(repository(missing.dataSource()).exists("pipeline", "1", ExecutionMode.RUN)).isFalse();
    }

    @Test
    void sqlFailures_shouldBeWrappedAsRepositoryExceptions() throws Exception {
        // Given
        DataSource dataSource = mock(DataSource.class);
        SQLException failure = new SQLException("db down");
        when(dataSource.getConnection()).thenThrow(failure);
        OperationChainObjectRepositoryJdbc repository = repository(dataSource);

        // When / Then
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> repository.exists("pipeline", "1",
                                                                                          ExecutionMode.RUN)))
                .isInstanceOf(OperationChainRepositoryException.class)
                .hasMessageContaining("check operation-chain object pipeline:1:RUN")
                .hasCause(failure);
    }

    private static void stubObjectRow(ResultSet resultSet,
                                      long id,
                                      String hash,
                                      Timestamp createdAt,
                                      Timestamp publishedAt)
            throws Exception {
        when(resultSet.getLong("id")).thenReturn(id);
        when(resultSet.getString("al_id")).thenReturn("pipeline");
        when(resultSet.getString("version")).thenReturn("1");
        when(resultSet.getString("publication_mode")).thenReturn("RUN");
        when(resultSet.getString("content_hash")).thenReturn(hash);
        when(resultSet.getLong("size_bytes")).thenReturn(10L);
        when(resultSet.getString("mime_type")).thenReturn("application/xml");
        when(resultSet.getTimestamp(eq("created_at"), any(Calendar.class))).thenReturn(createdAt);
        when(resultSet.getString("created_by")).thenReturn("tester");
        when(resultSet.getTimestamp(eq("published_at"), any(Calendar.class))).thenReturn(publishedAt);
    }

    private static OperationChainObjectRepositoryJdbc repository(DataSource dataSource) {
        return OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
    }

    private record JdbcMocks(DataSource dataSource,
                             Connection connection,
                             PreparedStatement statement,
                             ResultSet resultSet) {
        static JdbcMocks writeWithGeneratedKey(long id) throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet keys = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS)))
                    .thenReturn(statement);
            when(statement.executeUpdate()).thenReturn(1);
            when(statement.getGeneratedKeys()).thenReturn(keys);
            when(keys.next()).thenReturn(true);
            when(keys.getLong(1)).thenReturn(id);
            return new JdbcMocks(dataSource, connection, statement, keys);
        }

        static JdbcMocks query(boolean rowExists) throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet resultSet = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(rowExists);
            return new JdbcMocks(dataSource, connection, statement, resultSet);
        }
    }
}
