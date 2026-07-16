package io.github.gear4jtest.external.jdbc.repository;

import java.sql.PreparedStatement;
import java.sql.Types;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

    @Test
    void bindExecutionMode_shouldUsePostgresqlNativeTypeAndStringsElsewhere() throws Exception {
        // Given
        PreparedStatement postgresqlStatement = mock(PreparedStatement.class);
        PreparedStatement oracleStatement = mock(PreparedStatement.class);

        // When
        ExternalRepositorySqlDialect.bindExecutionMode(Gear4jDatabaseDialect.POSTGRESQL,
                                                       postgresqlStatement,
                                                       3,
                                                       ExecutionMode.RUN);
        ExternalRepositorySqlDialect.bindExecutionMode(Gear4jDatabaseDialect.ORACLE,
                                                       oracleStatement,
                                                       3,
                                                       ExecutionMode.RUN);

        // Then
        verify(postgresqlStatement).setObject(3, "RUN", Types.OTHER);
        verify(oracleStatement).setString(3, "RUN");
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
