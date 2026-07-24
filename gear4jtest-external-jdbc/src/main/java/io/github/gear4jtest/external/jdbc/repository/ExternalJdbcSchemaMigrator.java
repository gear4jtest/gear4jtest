package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.util.Objects;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.migration.JdbcSchemaMigrator;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;

/** Applies versioned Gear4J external-api JDBC schema migrations. */
public final class ExternalJdbcSchemaMigrator {
    private final JdbcSchemaMigrator delegate;

    public ExternalJdbcSchemaMigrator(Gear4jDatabaseDialect dialect) {
        this(dialect, false);
    }

    public ExternalJdbcSchemaMigrator(Gear4jDatabaseDialect dialect, boolean baselineOnMigrate) {
        Objects.requireNonNull(dialect, "dialect must not be null");
        this.delegate = JdbcSchemaMigrator.builder()
                .moduleId("gear4j-external-api")
                .dialect(dialect)
                .migrationListResource("io/github/gear4j/external/db/" + resourceDirectory(dialect)
                        + "/migrations/migrations.list")
                .baselineTableName("operation_chain_config")
                .baselineOnMigrate(baselineOnMigrate)
                .requiredColumns("artifact_store", "hash_hex", "size_bytes", "content", "created_at")
                .requiredColumns("operation_chain_config", "al_id", "allow_run_publication_without_test",
                                 "store_type", "store_props", "created_at", "updated_at")
                .requiredColumns("operation_chain_object", "id", "al_id", "version", "publication_mode",
                                 "content_hash", "size_bytes", "mime_type", "created_at", "created_by",
                                 "published_at")
                .requiredColumns("operation_chain_tag", "al_id", "tag")
                .requiredIndexes("operation_chain_object", "idx_op_chain_latest_run", "idx_op_chain_by_hash")
                .requiredIndexes("operation_chain_tag", "idx_tag_value")
                .build();
    }

    public static ExternalJdbcSchemaMigrator forDialect(Gear4jDatabaseDialect dialect) {
        return new ExternalJdbcSchemaMigrator(dialect);
    }

    public static ExternalJdbcSchemaMigrator forDialect(Gear4jDatabaseDialect dialect, boolean baselineOnMigrate) {
        return new ExternalJdbcSchemaMigrator(dialect, baselineOnMigrate);
    }

    public void migrate(DataSource dataSource) {
        delegate.migrate(dataSource);
    }

    public void migrate(Connection connection) {
        delegate.migrate(connection);
    }

    /**
     * Clears an incomplete migration marker after operator inspection so a retry
     * can be attempted.
     */
    public void prepareRetry(DataSource dataSource, String version) {
        delegate.prepareRetry(dataSource, version);
    }

    /**
     * Connection-scoped variant of {@link #prepareRetry(DataSource, String)}.
     */
    public void prepareRetry(Connection connection, String version) {
        delegate.prepareRetry(connection, version);
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
