package io.test.gear4jtest.external.api.repository.jdbc;

import java.sql.Connection;
import java.util.Objects;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.migration.JdbcSchemaMigrator;

/** Applies versioned Gear4J external-api JDBC schema migrations. */
public final class ExternalJdbcSchemaMigrator {
    private final JdbcSchemaMigrator delegate;

    public ExternalJdbcSchemaMigrator(Gear4jDatabaseDialect dialect) {
        Objects.requireNonNull(dialect, "dialect must not be null");
        this.delegate = new JdbcSchemaMigrator("gear4j-external-api", dialect,
                "io/github/gear4j/external/db/" + resourceDirectory(dialect) + "/migrations/migrations.list",
                "operation_chain_config");
    }

    public static ExternalJdbcSchemaMigrator forDialect(Gear4jDatabaseDialect dialect) {
        return new ExternalJdbcSchemaMigrator(dialect);
    }

    public void migrate(DataSource dataSource) {
        delegate.migrate(dataSource);
    }

    public void migrate(Connection connection) {
        delegate.migrate(connection);
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
