package io.github.gear4jtest.jdbc.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.PageRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Gear4jDatabaseDialectTest {
    @Test
    void setJson_shouldBindNullAndValuesUsingDialectSpecificJdbcTypes() throws Exception {
        // Given
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("payload")).thenReturn("{\"a\":1}");

        // When
        Gear4jDatabaseDialect.POSTGRESQL.setJson(statement, 1, null);
        Gear4jDatabaseDialect.ORACLE.setJson(statement, 2, null);
        Gear4jDatabaseDialect.H2.setJson(statement, 3, null);
        Gear4jDatabaseDialect.POSTGRESQL.setJson(statement, 4, "{\"a\":1}");
        Gear4jDatabaseDialect.MYSQL.setJson(statement, 5, "{\"b\":2}");
        Gear4jDatabaseDialect.ORACLE.setJson(statement, 6, "abc");

        // Then
        verify(statement).setNull(1, Types.OTHER);
        verify(statement).setNull(2, Types.CLOB);
        verify(statement).setNull(3, Types.VARCHAR);
        verify(statement).setObject(4, "{\"a\":1}", Types.OTHER);
        verify(statement).setString(5, "{\"b\":2}");
        verify(statement).setCharacterStream(eq(6), any(java.io.Reader.class), eq(3));
        assertThat(Gear4jDatabaseDialect.ORACLE.getJson(resultSet, "payload")).isEqualTo("{\"a\":1}");
    }

    @Test
    void instantBinding_shouldUseOffsetDateTimeForPostgresAndTimestampElsewhere() throws Exception {
        // Given
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        Instant instant = Instant.parse("2026-06-22T08:00:00Z");
        OffsetDateTime offsetDateTime = instant.atOffset(ZoneOffset.UTC);
        when(resultSet.getObject("created_at", OffsetDateTime.class)).thenReturn(offsetDateTime);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(instant));

        // When
        Gear4jDatabaseDialect.POSTGRESQL.setInstant(statement, 1, null);
        Gear4jDatabaseDialect.H2.setInstant(statement, 2, null);
        Gear4jDatabaseDialect.POSTGRESQL.setInstant(statement, 3, instant);
        Gear4jDatabaseDialect.H2.setInstant(statement, 4, instant);

        // Then
        verify(statement).setNull(1, Types.TIMESTAMP_WITH_TIMEZONE);
        verify(statement).setNull(2, Types.TIMESTAMP);
        verify(statement).setObject(3, offsetDateTime);
        verify(statement).setTimestamp(4, Timestamp.from(instant));
        assertThat(Gear4jDatabaseDialect.POSTGRESQL.getInstant(resultSet, "created_at")).isEqualTo(instant);
        assertThat(Gear4jDatabaseDialect.H2.getInstant(resultSet, "created_at")).isEqualTo(instant);
    }

    @Test
    void uuidBinding_shouldSupportNativeAndStringBackedDialects() throws Exception {
        // Given
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        UUID uuid = UUID.fromString("00000000-0000-7000-8000-000000000123");
        when(resultSet.getObject("id")).thenReturn(uuid);
        when(resultSet.getString("id")).thenReturn(uuid.toString(), " ", null);

        // When
        Gear4jDatabaseDialect.POSTGRESQL.setUuid(statement, 1, null);
        Gear4jDatabaseDialect.H2.setUuid(statement, 2, null);
        Gear4jDatabaseDialect.POSTGRESQL.setUuid(statement, 3, uuid);
        Gear4jDatabaseDialect.H2.setUuid(statement, 4, uuid);

        // Then
        verify(statement).setNull(1, Types.OTHER);
        verify(statement).setNull(2, Types.VARCHAR);
        verify(statement).setObject(3, uuid);
        verify(statement).setString(4, uuid.toString());
        assertThat(Gear4jDatabaseDialect.POSTGRESQL.getUuid(resultSet, "id")).isEqualTo(uuid);
        assertThat(Gear4jDatabaseDialect.H2.getUuid(resultSet, "id")).isEqualTo(uuid);
        assertThat(Gear4jDatabaseDialect.H2.getUuid(resultSet, "id")).isNull();
        assertThat(Gear4jDatabaseDialect.H2.getUuid(resultSet, "id")).isNull();
    }

    @Test
    void pagingAndUniqueViolation_shouldUseDialectSpecificRules() throws Exception {
        // Given
        PreparedStatement statement = mock(PreparedStatement.class);
        PageRequest page = new PageRequest(20, 10);

        // When
        Gear4jDatabaseDialect.ORACLE.bindPage(statement, 1, page);
        Gear4jDatabaseDialect.POSTGRESQL.bindPage(statement, 3, page);

        // Then
        assertThat(Gear4jDatabaseDialect.ORACLE.pagedSql("select * from t"))
                .endsWith("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        assertThat(Gear4jDatabaseDialect.H2.pagedSql("select * from t"))
                .endsWith("LIMIT ? OFFSET ?");
        verify(statement).setInt(1, 20);
        verify(statement).setInt(2, 10);
        verify(statement).setInt(3, 10);
        verify(statement).setInt(4, 20);
        assertThat(Gear4jDatabaseDialect.POSTGRESQL.supportsNativeStationLogUpsert()).isTrue();
        assertThat(Gear4jDatabaseDialect.H2.supportsNativeStationLogUpsert()).isFalse();
        assertThat(Gear4jDatabaseDialect.POSTGRESQL.isUniqueViolation(new java.sql.SQLException("", "23505")))
                .isTrue();
        assertThat(Gear4jDatabaseDialect.MYSQL.isUniqueViolation(new java.sql.SQLException("", "", 1062))).isTrue();
        assertThat(Gear4jDatabaseDialect.ORACLE.isUniqueViolation(new java.sql.SQLException("", "", 1))).isTrue();
        assertThat(Gear4jDatabaseDialect.H2.isUniqueViolation(new java.sql.SQLException("duplicate key"))).isTrue();
        assertThat(Gear4jDatabaseDialect.H2.isUniqueViolation(new java.sql.SQLException("other"))).isFalse();
    }
}
