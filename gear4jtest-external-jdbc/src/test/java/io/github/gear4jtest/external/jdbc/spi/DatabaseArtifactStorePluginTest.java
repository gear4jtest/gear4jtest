package io.github.gear4jtest.external.jdbc.spi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.spi.ArtifactStorePlugin;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DatabaseArtifactStorePluginTest {
    @TempDir
    Path tempDirectory;

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
    void build_shouldAcceptASupportedExplicitDialect() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When
        var store = plugin.build(Map.of("dialect", "postgresql"), context);

        // Then
        assertThat(store).isInstanceOf(DatabaseArtifactStore.class);
    }

    @Test
    void build_shouldApplyArtifactSizeLimit() throws Exception {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();
        DatabaseArtifactStore store = (DatabaseArtifactStore) plugin.build(
                                                                           Map.of("dialect", "h2",
                                                                                  "maxArtifactSizeBytes", "3"),
                                                                           context);

        // When / Then
        assertThatThrownBy(() -> store.put(new byte[4]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=3");
        verifyNoInteractions(dataSource);
    }

    @Test
    void build_shouldRejectInvalidArtifactSizeLimit() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of("dialect", "h2", "maxArtifactSizeBytes", "-2"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxArtifactSizeBytes");
    }

    @Test
    void build_shouldApplySpoolPolicy() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();
        DatabaseArtifactStore store = (DatabaseArtifactStore) plugin.build(
                                                                           Map.of("dialect", "h2", "spoolDirectory",
                                                                                  tempDirectory.toString(),
                                                                                  "spoolMaxBytes", "3",
                                                                                  "spoolStaleFileAge", "PT1H"),
                                                                           context);

        // When / Then
        assertThatThrownBy(() -> store.put(new byte[4]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("spool quota exceeded");
        assertThat(store.snapshotSpoolStats().quotaRejections()).isEqualTo(1L);
        verifyNoInteractions(dataSource);
    }

    @Test
    void build_shouldRejectInvalidSpoolDuration() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of("dialect", "h2", "spoolStaleFileAge", "24 hours"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spoolStaleFileAge");
    }

    @Test
    void build_shouldRejectInvalidPrivatePermissionRequirement() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of("dialect", "h2", "requirePrivatePermissions", "sometimes"),
                                              context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requirePrivatePermissions")
                .hasMessageContaining("true or false");
    }
}
