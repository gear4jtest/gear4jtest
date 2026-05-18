package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;

public enum JdbcDialect {
    POSTGRES,
    MYSQL8,
    MARIADB;

    public static JdbcDialect from(DatabaseMetaData metaData) throws SQLException {
        Objects.requireNonNull(metaData, "metaData must not be null");
        String productName = nullToBlank(metaData.getDatabaseProductName());
        String driverName = nullToBlank(metaData.getDriverName());
        String url = nullToBlank(metaData.getURL());
        String fingerprint = (productName + "\n" + driverName + "\n" + url).toLowerCase(Locale.ROOT);

        if (fingerprint.contains("mariadb")) {
            return MARIADB;
        }
        if (fingerprint.contains("postgresql") || fingerprint.contains("postgres")) {
            return POSTGRES;
        }
        if (fingerprint.contains("mysql")) {
            return MYSQL8;
        }
        throw new UnsupportedOperationException(
                "Unsupported Gear4J external repository database provider. productName='"
                        + productName + "', driverName='" + driverName + "', url='" + url
                        + "'. Supported providers are PostgreSQL, MySQL 8 and MariaDB.");
    }

    public boolean isPostgres() {
        return this == POSTGRES;
    }

    public boolean isMySqlCompatible() {
        return this == MYSQL8 || this == MARIADB;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
