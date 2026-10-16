package io.github.gear4jtest.studio.jdbc;

import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.migration.JdbcSchemaMigrator;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;

/**
 * Separate management schema. Hosts may run these V1 resources through their
 * own migration tool.
 */
public final class StudioJdbcSchema {
    private StudioJdbcSchema() {
    }

    public static JdbcSchemaMigrator migrator(Gear4jDatabaseDialect dialect) {
        if (dialect != Gear4jDatabaseDialect.H2 && dialect != Gear4jDatabaseDialect.POSTGRESQL)
            throw new IllegalArgumentException("Studio supports H2 and PostgreSQL");
        return JdbcSchemaMigrator.builder().moduleId("gear4j-studio")
                .dialect(dialect).classLoader(StudioJdbcSchema.class.getClassLoader())
                .migrationListResource("io/github/gear4j/studio/db/" + dialect.resourceDirectory()
                        + "/migrations/migrations.list")
                .baselineTableName("g4js_resource").build();
    }

    public static void migrate(DataSource dataSource, Gear4jDatabaseDialect dialect) {
        migrator(dialect).migrate(dataSource);
    }
}
