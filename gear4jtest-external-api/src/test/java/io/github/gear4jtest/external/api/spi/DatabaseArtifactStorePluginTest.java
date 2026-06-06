package io.github.gear4jtest.external.api.spi;

import java.util.Map;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.artifact.DatabaseArtifactStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class DatabaseArtifactStorePluginTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final ArtifactStorePlugin.Context context = key -> dataSource;

    @Test
    void build_shouldRequireAnExplicitDialect() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of(), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires property 'dialect'");
    }

    @Test
    void build_shouldAcceptASupportedExplicitDialect() throws Exception {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When
        var store = plugin.build(Map.of("dialect", "postgresql"), context);

        // Then
        assertThat(store).isInstanceOf(DatabaseArtifactStore.class);
    }
}
