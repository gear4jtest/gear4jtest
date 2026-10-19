package io.github.gear4jtest.studio.jdbc;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.jdbc.migration.JdbcSchemaMigrator;
import io.github.gear4jtest.jdbc.migration.SchemaMigrationException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ds.PGSimpleDataSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Owns only a new random schema inside an explicitly disposable test database.
 */
@Tag("studio-release-postgres")
class RequiredPostgreSqlStudioIT {
    @TempDir
    Path directory;

    @Test
    void populatedBaselineRestoreChecksumsContextAndConcurrentRestart() throws Exception {
        // Given: no assumptions/skips; the release gate requires a real service.
        assertThat(System.getenv("GEAR4J_TEST_DATABASE_DISPOSABLE")).isEqualTo("true");
        var source = new PGSimpleDataSource();
        source.setURL(required("GEAR4J_TEST_POSTGRES_URL"));
        source.setUser(required("GEAR4J_TEST_POSTGRES_USER"));
        source.setPassword(required("GEAR4J_TEST_POSTGRES_PASSWORD"));
        source.setConnectTimeout(5);
        source.setSocketTimeout(15);
        String schema = "studio_rr_" + UUID.randomUUID().toString().replace("-", "");
        source.setCurrentSchema(schema);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(17);
            statement.execute("CREATE SCHEMA " + schema);
        }
        try {
            // When: same checks as the Docker integration test, then real backup/restore.
            PostgreSqlStudioStoreIT.qualify(source);
            new JdbcStudioStore(source).bindContext("context");
            recoveryAfterFailedDdl(source);
            var before = snapshot(source, schema);
            Path dump = directory.resolve("studio.dump");
            pg(source, "pg_dump", "--format=custom", "--no-owner", "--no-privileges", "--schema=" + schema,
               "--file=" + dump);
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
            pg(source, "pg_restore", "--exit-on-error", "--no-owner", "--no-privileges", dump.toString());
            // Then: every stored row, history, ACL and source survives exactly.
            assertThat(snapshot(source, schema)).isEqualTo(before);
            var workers = Executors.newFixedThreadPool(2);
            try {
                var first = workers.submit(() -> StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.POSTGRESQL));
                var second = workers.submit(() -> StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.POSTGRESQL));
                first.get(20, TimeUnit.SECONDS);
                second.get(20, TimeUnit.SECONDS);
            } finally {
                workers.shutdownNow();
            }
            assertThatThrownBy(() -> new JdbcStudioStore(source).bindContext("other.context"))
                    .isInstanceOf(IllegalStateException.class);
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement
                        .executeUpdate("UPDATE gear4j_schema_history SET checksum='changed' WHERE module_id='gear4j-studio'");
            }
            assertThatThrownBy(() -> StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.POSTGRESQL))
                    .isInstanceOf(SchemaMigrationException.class).hasMessageContaining("Checksum mismatch");
        } finally {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private static void recoveryAfterFailedDdl(PGSimpleDataSource source) throws Exception {
        // A missing prerequisite fails after CREATE TABLE. Retry uses unchanged bytes.
        var loader = new ClassLoader(null) {
            @Override
            public java.io.InputStream getResourceAsStream(String name) {
                String text = name.endsWith("migrations.list") ? "V1__probe.sql"
                        : "CREATE TABLE rr_probe(id INTEGER); SELECT id FROM rr_prerequisite;";
                return new java.io.ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        var migrator = JdbcSchemaMigrator.builder().moduleId("rr-recovery").dialect(Gear4jDatabaseDialect.POSTGRESQL)
                .migrationListResource("rr/migrations.list").baselineTableName("rr_probe").classLoader(loader).build();
        assertThatThrownBy(() -> migrator.migrate(source)).isInstanceOf(SchemaMigrationException.class);
        assertThat(migrator.migrationStatuses(source)).singleElement()
                .satisfies(s -> assertThat(s.state().name()).isEqualTo("FAILED"));
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT to_regclass('rr_probe')")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isNull();
            }
            statement.execute("CREATE TABLE rr_prerequisite(id INTEGER)");
        }
        assertThatThrownBy(() -> migrator.migrate(source)).isInstanceOf(SchemaMigrationException.class);
        migrator.prepareRetry(source, "1");
        migrator.migrate(source);
        assertThat(migrator.migrationStatuses(source)).singleElement()
                .satisfies(s -> assertThat(s.state().name()).isEqualTo("APPLIED"));
    }

    private static Map<String, List<String>> snapshot(PGSimpleDataSource source, String schema) throws SQLException {
        var result = new TreeMap<String, List<String>>();
        try (var connection = source.getConnection()) {
            var tables = new ArrayList<String>();
            try (var statement = connection.prepareStatement("SELECT tablename FROM pg_tables WHERE schemaname=?")) {
                statement.setString(1, schema);
                try (var rows = statement.executeQuery()) {
                    while (rows.next())
                        tables.add(rows.getString(1));
                }
            }
            for (String table : tables) {
                assertThat(table).matches("[a-z0-9_]+");
                var values = new ArrayList<String>();
                try (var statement = connection.createStatement();
                        var rows = statement
                                .executeQuery("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY 1")) {
                    while (rows.next())
                        values.add(rows.getString(1));
                }
                result.put(table, values);
            }
        }
        return result;
    }

    private void pg(PGSimpleDataSource source, String executable, String... arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(executable);
        if (executable.equals("pg_restore"))
            command.add("--dbname=" + source.getDatabaseName());
        command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(directory.resolve(executable + ".log").toFile());
        builder.environment().put("PGHOST", source.getServerNames()[0]);
        builder.environment().put("PGPORT", Integer.toString(source.getPortNumbers()[0]));
        builder.environment().put("PGDATABASE", source.getDatabaseName());
        builder.environment().put("PGUSER", source.getUser());
        builder.environment().put("PGPASSWORD", source.getPassword());
        builder.environment().put("PGCONNECT_TIMEOUT", "5");
        var process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as(executable + " deadline").isTrue();
            assertThat(process.exitValue()).as(executable + " exit").isZero();
        } finally {
            process.destroyForcibly();
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        assertThat(value).as(name + " required").isNotBlank();
        return value;
    }
}
