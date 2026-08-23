package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.repository.OperationChainObjectCursor;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStageCursor;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationChainObjectRepositoryJdbcTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Test
    void findAll_shouldAlwaysApplySqlLevelPagination() throws Exception {
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
    void findAll_shouldRejectMissingPageRequestBeforeOpeningConnection() {
        // Given
        DataSource dataSource = mock(DataSource.class);
        OperationChainObjectRepositoryJdbc repository = OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();

        // When / Then
        assertThatNullPointerException()
                .isThrownBy(() -> repository.findAll("pipeline", null))
                .withMessage("pageRequest must not be null");
        verifyNoInteractions(dataSource);
    }

    @Test
    void findAllAfter_shouldBindAStableKeysetCursorBeforeThePageLimit() throws Exception {
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
        Instant publishedAt = Instant.parse("2026-01-02T00:00:00Z");

        var result = repository.findAllAfter("pipeline", new OperationChainObjectCursor(publishedAt, 42L), 10);

        assertThat(result).isEmpty();
        assertThat(preparedSql[0])
                .contains("published_at < ? OR (published_at = ? AND id < ?)")
                .contains("ORDER BY published_at DESC, id DESC LIMIT ? OFFSET ?");
        verify(statement).setString(1, "pipeline");
        verify(statement).setTimestamp(eq(2), eq(Timestamp.from(publishedAt)), any(Calendar.class));
        verify(statement).setTimestamp(eq(3), eq(Timestamp.from(publishedAt)), any(Calendar.class));
        verify(statement).setLong(4, 42L);
        verify(statement).setInt(5, 10);
        verify(statement).setInt(6, 0);
    }

    @Test
    void find_shouldMapRequiredTimestamps() throws Exception {
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
        when(resultSet.getString("publication_mode")).thenReturn("RUN");
        when(resultSet.getString("content_hash")).thenReturn("a".repeat(64));
        when(resultSet.getLong("size_bytes")).thenReturn(123L);
        when(resultSet.getString("mime_type")).thenReturn("application/xml");
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant publishedAt = Instant.parse("2026-01-02T00:00:00Z");
        when(resultSet.getTimestamp(eq("created_at"), any(Calendar.class)))
                .thenReturn(Timestamp.from(createdAt));
        when(resultSet.getTimestamp(eq("published_at"), any(Calendar.class)))
                .thenReturn(Timestamp.from(publishedAt));

        OperationChainObjectRepositoryJdbc repository = OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();

        // When
        var result = repository.find("pipeline", "1.0.0", ExecutionMode.RUN).orElseThrow();

        // Then
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.publishedAt()).isEqualTo(publishedAt);
    }

    @Test
    void findStagedBefore_shouldLoadThePagedStagesAndTheirTagsWithOneQuery() throws Exception {
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
        when(resultSet.next()).thenReturn(true, true, true, false);
        when(resultSet.getString("stage_id")).thenReturn("stage-a", "stage-a", "stage-b");
        when(resultSet.getString("al_id")).thenReturn("line-a", "line-a", "line-b");
        when(resultSet.getString("version")).thenReturn("1", "1", "2");
        when(resultSet.getString("publication_mode")).thenReturn("TEST", "TEST", "RUN");
        when(resultSet.getString("content_hash")).thenReturn(HASH_A, HASH_A, HASH_B);
        when(resultSet.getLong("size_bytes")).thenReturn(10L, 10L, 20L);
        when(resultSet.getString("mime_type")).thenReturn("application/xml");
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant publishedAt = Instant.parse("2026-01-02T00:00:00Z");
        Instant stagedAt = Instant.parse("2026-01-03T00:00:00Z");
        when(resultSet.getTimestamp(eq("created_at"), any(Calendar.class)))
                .thenReturn(Timestamp.from(createdAt));
        when(resultSet.getString("created_by")).thenReturn("tester");
        when(resultSet.getTimestamp(eq("published_at"), any(Calendar.class)))
                .thenReturn(Timestamp.from(publishedAt));
        when(resultSet.getString("store_fingerprint")).thenReturn(HASH_A, HASH_A, HASH_B);
        when(resultSet.getTimestamp(eq("staged_at"), any(Calendar.class)))
                .thenReturn(Timestamp.from(stagedAt));
        when(resultSet.getLong("stage_revision")).thenReturn(1L, 1L, 2L);
        when(resultSet.getString("stage_tag")).thenReturn("alpha", "omega", null);
        OperationChainObjectRepositoryJdbc repository = OperationChainObjectRepositoryJdbc.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();

        // When
        var result = repository.findStagedBefore(stagedAt.plusSeconds(1), new PageRequest(5, 10));

        // Then
        assertThat(result)
                .extracting(OperationChainPublicationStage::stageId)
                .containsExactly("stage-a", "stage-b");
        assertThat(result.get(0).tags()).containsExactly("alpha", "omega");
        assertThat(result.get(1).tags()).isEmpty();
        assertThat(preparedSql[0])
                .contains("FROM (SELECT")
                .contains("LIMIT ? OFFSET ?")
                .contains("LEFT JOIN operation_chain_publication_stage_tag")
                .contains("ORDER BY staged_page.staged_at, staged_page.stage_id, tag_row.tag");
        verify(connection, times(1)).prepareStatement(anyString());
        verify(statement, times(1)).executeQuery();
        verify(statement).setInt(2, 10);
        verify(statement).setInt(3, 5);
    }

    @Test
    void findStagedAfter_shouldBindTheAgeAndIdentifierCursor() throws Exception {
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
        Instant cutoff = Instant.parse("2026-01-04T00:00:00Z");
        Instant stagedAt = Instant.parse("2026-01-03T00:00:00Z");

        var result = repository.findStagedAfter(cutoff,
                                                new OperationChainPublicationStageCursor(stagedAt, "stage-a"), 10);

        assertThat(result).isEmpty();
        assertThat(preparedSql[0])
                .contains("staged_at > ? OR (staged_at = ? AND stage_id > ?)")
                .contains("ORDER BY staged_at, stage_id LIMIT ? OFFSET ?");
        verify(statement).setTimestamp(eq(2), eq(Timestamp.from(stagedAt)), any(Calendar.class));
        verify(statement).setTimestamp(eq(3), eq(Timestamp.from(stagedAt)), any(Calendar.class));
        verify(statement).setString(4, "stage-a");
        verify(statement).setInt(5, 10);
        verify(statement).setInt(6, 0);
    }
}
