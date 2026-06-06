package io.github.gear4jtest.external.api.artifact;

import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DatabaseArtifactStoreTest {
    @Test
    void should_reject_invalid_table_names() {
        // Given
        DataSource dataSource = mock(DataSource.class);

        // When / Then
        assertThatThrownBy(() -> new DatabaseArtifactStore(dataSource, "artifact_store;drop table users",
                Gear4jDatabaseDialect.POSTGRESQL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabaseArtifactStore(dataSource, "schema.artifact_store",
                Gear4jDatabaseDialect.POSTGRESQL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_accept_simple_table_names_whenDialectIsExplicit() {
        // Given
        DataSource dataSource = mock(DataSource.class);

        // When / Then
        assertThatCode(() -> new DatabaseArtifactStore(dataSource, "gear4j_artifacts_1",
                Gear4jDatabaseDialect.POSTGRESQL)).doesNotThrowAnyException();
        assertThatCode(() -> new DatabaseArtifactStore(dataSource, null, Gear4jDatabaseDialect.POSTGRESQL))
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_missing_dialect() {
        // Given
        DataSource dataSource = mock(DataSource.class);

        // When / Then
        assertThatThrownBy(() -> new DatabaseArtifactStore(dataSource, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("databaseDialect must not be null");
    }
}
