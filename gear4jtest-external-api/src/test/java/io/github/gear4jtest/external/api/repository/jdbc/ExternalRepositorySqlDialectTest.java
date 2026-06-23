package io.github.gear4jtest.external.api.repository.jdbc;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalRepositorySqlDialectTest {
    @Test
    void migrationListResource_shouldExistForEveryConfiguredDialect() {
        // When / Then
        for (Gear4jDatabaseDialect dialect : Gear4jDatabaseDialect.values()) {
            String resource = "io/github/gear4j/external/db/" + resourceDirectory(dialect)
                    + "/migrations/migrations.list";
            assertThat(getClass().getClassLoader().getResource(resource))
                    .as("migration list resource for %s", dialect)
                    .isNotNull();
        }
    }

    private static String resourceDirectory(Gear4jDatabaseDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL -> "postgresql";
            case MYSQL -> "mysql";
            case MARIADB -> "mariadb";
            case ORACLE -> "oracle";
            case H2 -> "h2";
        };
    }
}
