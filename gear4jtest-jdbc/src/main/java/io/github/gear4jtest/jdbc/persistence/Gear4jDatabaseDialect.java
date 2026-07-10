package io.github.gear4jtest.jdbc.persistence;

import java.io.StringReader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import io.github.gear4jtest.core.persistence.PageRequest;

/**
 * Database dialect explicitly selected by applications using Gear4J JDBC
 * components.
 *
 * <p>
 * Gear4J deliberately does not infer the dialect from JDBC metadata. The chosen
 * dialect controls migration resources, SQL syntax and JDBC bindings; making it
 * mandatory keeps startup deterministic and prevents an ambiguous driver or
 * proxy from silently selecting an incompatible SQL path.
 * </p>
 */
public enum Gear4jDatabaseDialect {
    POSTGRESQL("PostgreSQL", "postgresql", true),
    MYSQL("MySQL", "mysql", false),
    MARIADB("MariaDB", "mysql", false),
    ORACLE("Oracle", "oracle", false),
    H2("H2", "h2", false);

    private final String displayName;
    private final String resourceDirectory;
    private final boolean nativeUuid;

    Gear4jDatabaseDialect(String displayName, String resourceDirectory, boolean nativeUuid) {
        this.displayName = displayName;
        this.resourceDirectory = resourceDirectory;
        this.nativeUuid = nativeUuid;
    }

    /**
     * Returns the resource directory containing Gear4J internal migrations for this
     * dialect.
     */
    public String resourceDirectory() {
        return resourceDirectory;
    }

    /** Minimal provider-specific query used by current-connectivity probes. */
    public String validationQuery() {
        return this == ORACLE ? "SELECT 1 FROM dual" : "SELECT 1";
    }

    void setJson(PreparedStatement stmt, int index, String json) throws SQLException {
        if (json == null) {
            stmt.setNull(index, this == POSTGRESQL ? Types.OTHER : this == ORACLE ? Types.CLOB : Types.VARCHAR);
            return;
        }
        if (this == POSTGRESQL) {
            stmt.setObject(index, json, Types.OTHER);
        } else if (this == ORACLE) {
            stmt.setCharacterStream(index, new StringReader(json), json.length());
        } else {
            stmt.setString(index, json);
        }
    }

    String getJson(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
    }

    /** Binds an instant without depending on the JVM default time zone. */
    public void setInstant(PreparedStatement stmt, int index, Instant value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, this == POSTGRESQL ? Types.TIMESTAMP_WITH_TIMEZONE : Types.TIMESTAMP);
            return;
        }
        if (this == POSTGRESQL) {
            stmt.setObject(index, value.atOffset(ZoneOffset.UTC));
        } else {
            stmt.setTimestamp(index, Timestamp.from(value), utcCalendar());
        }
    }

    /** Reads an instant without depending on the JVM default time zone. */
    public Instant getInstant(ResultSet rs, String column) throws SQLException {
        if (this == POSTGRESQL) {
            OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
            return value != null ? value.toInstant() : null;
        }
        Timestamp value = rs.getTimestamp(column, utcCalendar());
        return value != null ? value.toInstant() : null;
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    void setUuid(PreparedStatement stmt, int index, UUID value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, nativeUuid ? Types.OTHER : Types.VARCHAR);
            return;
        }
        if (nativeUuid) {
            stmt.setObject(index, value);
        } else {
            stmt.setString(index, value.toString());
        }
    }

    UUID getUuid(ResultSet rs, String column) throws SQLException {
        if (nativeUuid) {
            Object value = rs.getObject(column);
            if (value == null) {
                return null;
            }
            if (value instanceof UUID uuid) {
                return uuid;
            }
            return UUID.fromString(value.toString());
        }

        String value = rs.getString(column);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    boolean supportsNativeStationLogUpsert() {
        return this == POSTGRESQL || this == MYSQL || this == MARIADB;
    }

    String pagedSql(String orderedSql) {
        return this == ORACLE ? orderedSql + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
                : orderedSql + " LIMIT ? OFFSET ?";
    }

    void bindPage(PreparedStatement stmt, int firstParameterIndex, PageRequest pageRequest) throws SQLException {
        if (this == ORACLE) {
            stmt.setInt(firstParameterIndex, pageRequest.offset());
            stmt.setInt(firstParameterIndex + 1, pageRequest.limit());
        } else {
            stmt.setInt(firstParameterIndex, pageRequest.limit());
            stmt.setInt(firstParameterIndex + 1, pageRequest.offset());
        }
    }

    boolean isUniqueViolation(SQLException exception) {
        String state = exception.getSQLState();
        int code = exception.getErrorCode();
        if ("23505".equals(state)) {
            return true;
        }
        if ((this == MYSQL || this == MARIADB) && code == 1062) {
            return true;
        }
        if (this == ORACLE && code == 1) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("duplicate");
    }

    @Override
    public String toString() {
        return displayName;
    }
}
