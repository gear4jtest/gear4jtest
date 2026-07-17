package io.github.gear4jtest.external.jdbc.repository;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalRepositorySqlDialectContractTest {
    @ParameterizedTest
    @EnumSource(Gear4jDatabaseDialect.class)
    void sqlHelpers_shouldExposeStatementsForEverySupportedDialect(Gear4jDatabaseDialect dialect) {
        // When
        String upsert = ExternalRepositorySqlDialect.upsertOperationChainConfigSql(dialect);
        String tagInsert = ExternalRepositorySqlDialect.insertTagIfAbsentSql(dialect);
        String updateStore = ExternalRepositorySqlDialect.updateOperationChainStoreSql(dialect);

        // Then
        assertThat(upsert)
                .as("config upsert SQL for %s", dialect)
                .contains("operation_chain_config")
                .isNotBlank();
        assertThat(tagInsert)
                .as("tag insert-if-absent SQL for %s", dialect)
                .contains("operation_chain_tag")
                .isNotBlank();
        assertThat(updateStore)
                .as("store update SQL for %s", dialect)
                .contains("operation_chain_config")
                .contains("store_type")
                .isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(Gear4jDatabaseDialect.class)
    void migrationList_shouldReferenceExistingVersionedScriptsForEveryDialect(Gear4jDatabaseDialect dialect)
            throws Exception {
        // Given
        String base = "io/github/gear4j/external/db/" + resourceDirectory(dialect) + "/migrations/";
        var classLoader = getClass().getClassLoader();
        var migrationList = classLoader.getResource(base + "migrations.list");

        // When / Then
        assertThat(migrationList).as("migration list for %s", dialect).isNotNull();
        String list = new String(migrationList.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        for (String script : list.lines().map(String::trim).filter(line -> !line.isEmpty()).toList()) {
            var scriptResource = classLoader.getResource(base + script);
            assertThat(scriptResource).as("listed external migration %s for %s exists", script, dialect).isNotNull();
            String sql = new String(scriptResource.openStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThat(sql)
                    .as("external migration %s for %s creates the core external tables", script, dialect)
                    .contains("operation_chain_config")
                    .contains("operation_chain_object")
                    .contains("publication_mode")
                    .contains("operation_chain_publication_stage")
                    .contains("operation_chain_publication_stage_tag")
                    .contains("stage_revision")
                    .contains("store_fingerprint")
                    .contains("operation_chain_tag")
                    .contains("artifact_store");
            if (dialect == Gear4jDatabaseDialect.ORACLE) {
                assertThat(sql)
                        .as("Oracle migration %s must not use reserved MODE as a column", script)
                        .doesNotContainPattern("(?im)^\\s*mode\\s+");
            }
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
