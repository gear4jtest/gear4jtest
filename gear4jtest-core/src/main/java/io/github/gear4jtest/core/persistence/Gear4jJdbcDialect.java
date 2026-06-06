package io.github.gear4jtest.core.persistence;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal JDBC dialect used by Gear4J persistence.
 *
 * <p>
 * This is intentionally not a generic SQL abstraction layer. It only captures
 * the database-specific behavior Gear4J actually needs today: JSON binding,
 * UUID binding and duplicate-key detection. Schema creation is handled by the
 * migration resources associated with {@link Gear4jDatabaseDialect}.
 * </p>
 */
public enum Gear4jJdbcDialect {
    POSTGRESQL("PostgreSQL", true),
    MYSQL("MySQL", false),
    MARIADB("MariaDB", false),
    H2("H2", false);

    private final String displayName;
    private final boolean nativeUuid;

    Gear4jJdbcDialect(String displayName, boolean nativeUuid) {
        this.displayName = displayName;
        this.nativeUuid = nativeUuid;
    }

    public static Gear4jJdbcDialect from(DatabaseMetaData metaData) throws SQLException {
        Objects.requireNonNull(metaData, "metaData must not be null");

        String productName = nullToBlank(metaData.getDatabaseProductName());
        String driverName = nullToBlank(metaData.getDriverName());
        String url = nullToBlank(metaData.getURL());
        String fingerprint = (productName + "\n" + driverName + "\n" + url).toLowerCase(Locale.ROOT);

        // MariaDB must be checked before MySQL because some MariaDB-compatible
        // stacks expose MySQL-flavoured product names or URLs.
        if (fingerprint.contains("mariadb")) {
            return MARIADB;
        }
        if (fingerprint.contains("postgresql") || fingerprint.contains("postgres")) {
            return POSTGRESQL;
        }
        if (fingerprint.contains("mysql")) {
            return MYSQL;
        }
        if (fingerprint.contains("h2")) {
            return H2;
        }

        throw new UnsupportedOperationException("Unsupported Gear4J database provider. productName='" + productName
                + "', driverName='" + driverName + "', url='" + url
                + "'. Supported providers are PostgreSQL, MySQL 8, MariaDB and H2.");
    }

    void setJson(PreparedStatement stmt, int index, String json) throws SQLException {
        if (json == null) {
            stmt.setNull(index, this == POSTGRESQL ? Types.OTHER : Types.VARCHAR);
            return;
        }
        if (this == POSTGRESQL) {
            stmt.setObject(index, json, Types.OTHER);
        } else {
            stmt.setString(index, json);
        }
    }

    String getJson(ResultSet rs, String column) throws SQLException {
        return rs.getString(column);
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

    boolean isUniqueViolation(SQLException exception) {
        String state = exception.getSQLState();
        int code = exception.getErrorCode();
        if ("23505".equals(state)) {
            return true;
        }
        if ((this == MYSQL || this == MARIADB) && code == 1062) {
            return true;
        }
        String message = exception.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("duplicate");
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
