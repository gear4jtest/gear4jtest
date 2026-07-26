package io.github.gear4jtest.jdbc.migration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationResourceLoaderTest {
    @Test
    void loadMigrations_shouldParseVersionedResourcesAndNormalizeLeadingSlash() throws Exception {
        // Given
        MigrationResourceLoader loader = new MigrationResourceLoader("/db/migrations.list", resources(Map.of(
                                                                                                             "db/migrations.list",
                                                                                                             "# comment\nV1__create_schema.sql\nsub/V2__add_index.sql\n",
                                                                                                             "db/V1__create_schema.sql",
                                                                                                             "CREATE TABLE sample(id INT);")));

        // When
        var migrations = loader.loadMigrations();

        // Then
        assertThat(migrations).containsExactly(
                                               new SchemaMigration("1", "create schema", "db/V1__create_schema.sql"),
                                               new SchemaMigration("2", "add index", "db/sub/V2__add_index.sql"));
        assertThat(loader.readResource("/db/V1__create_schema.sql"))
                .isEqualTo("CREATE TABLE sample(id INT);\n");
        assertThat(MigrationResourceLoader.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void loadMigrations_shouldRejectMalformedFileNames() {
        MigrationResourceLoader loader = new MigrationResourceLoader("db/migrations.list",
                resources(Map.of("db/migrations.list", "migration.sql\n")));

        assertThatThrownBy(loader::loadMigrations)
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Invalid Gear4J migration file name");
    }

    private static ClassLoader resources(Map<String, String> resources) {
        return new ClassLoader(null) {
            @Override
            public InputStream getResourceAsStream(String name) {
                String content = resources.get(name);
                return content != null
                        ? new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
                        : null;
            }
        };
    }
}
