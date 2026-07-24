package io.github.gear4jtest.external.api.spi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemArtifactStorePluginTest {
    private static final byte[] HELLO = "hello".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void build_shouldApplyConfiguredArtifactSizeLimit() throws Exception {
        // Given
        ArtifactStore store = new FilesystemArtifactStorePlugin()
                .build(Map.of("root", tempDir.toString(), "maxArtifactSizeBytes", "4"), null);

        // When / Then
        assertThatThrownBy(() -> store.put(HELLO))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=4");
    }

    @Test
    void build_shouldAllowExplicitLargerFiniteLimit() throws Exception {
        // Given
        ArtifactStore store = new FilesystemArtifactStorePlugin()
                .build(Map.of("root", tempDir.toString(), "maxArtifactSizeBytes", "6"), null);

        // When
        String hash = store.put(HELLO);

        // Then
        assertThat(store.get(hash)).isPresent();
    }

    @Test
    void build_shouldRejectInvalidArtifactSizeLimit() {
        assertThatThrownBy(() -> new FilesystemArtifactStorePlugin()
                .build(Map.of("root", tempDir.toString(), "maxArtifactSizeBytes", "unbounded"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxArtifactSizeBytes");
    }
}
