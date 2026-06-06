package io.github.gear4jtest.external.api.repository.jdbc;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalRepositorySqlDialectTest {
    @Test
    void schemaResource_shouldExistForEveryConfiguredDialect() {
        // When / Then
        for (Gear4jDatabaseDialect dialect : Gear4jDatabaseDialect.values()) {
            assertThat(getClass().getClassLoader().getResource(ExternalRepositorySqlDialect.schemaResource(dialect)))
                    .as("schema resource for %s", dialect)
                    .isNotNull();
        }
    }
}
