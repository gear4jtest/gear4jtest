package io.github.gear4jtest.external.jdbc.repository;

import java.lang.reflect.Proxy;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDialectCoverageTest {
    @Test
    void from_shouldDetectSupportedDialectsFromMetadataFingerprint() throws Exception {
        assertThat(JdbcDialect.from(meta("PostgreSQL", "PostgreSQL JDBC Driver", "jdbc:postgresql://localhost/db")))
                .isEqualTo(JdbcDialect.POSTGRES);
        assertThat(JdbcDialect.from(meta("MySQL", "MySQL Connector/J", "jdbc:mysql://localhost/db")))
                .isEqualTo(JdbcDialect.MYSQL8);
        assertThat(JdbcDialect.from(meta("MySQL", "MariaDB Connector/J", "jdbc:mysql://localhost/db")))
                .isEqualTo(JdbcDialect.MARIADB);
    }

    @Test
    void from_shouldRejectUnknownMetadataAndNullMetadata() {
        DatabaseMetaData unknownMetadata = meta("H2", "H2 JDBC Driver", "jdbc:h2:mem:test");

        assertThatThrownBy(() -> JdbcDialect.from(unknownMetadata))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported Gear4J external repository database provider");
        assertThatNullPointerException()
                .isThrownBy(() -> JdbcDialect.from(null))
                .withMessage("metaData must not be null");
    }

    @Test
    void helperMethods_shouldExposeDialectFamilies() {
        assertThat(JdbcDialect.POSTGRES.isPostgres()).isTrue();
        assertThat(JdbcDialect.MYSQL8.isPostgres()).isFalse();
        assertThat(JdbcDialect.MYSQL8.isMySqlCompatible()).isTrue();
        assertThat(JdbcDialect.MARIADB.isMySqlCompatible()).isTrue();
        assertThat(JdbcDialect.POSTGRES.isMySqlCompatible()).isFalse();
    }

    private static DatabaseMetaData meta(String productName, String driverName, String url) {
        return (DatabaseMetaData) Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(),
                                                         new Class<?>[] { DatabaseMetaData.class },
                                                         (proxy, method, args) -> switch (method.getName()) {
                                                             case "getDatabaseProductName" -> productName;
                                                             case "getDriverName" -> driverName;
                                                             case "getURL" -> url;
                                                             case "toString" -> "DatabaseMetaDataStub";
                                                             default -> throw new SQLException(
                                                                     "Unexpected metadata call: " + method.getName());
                                                         });
    }
}
