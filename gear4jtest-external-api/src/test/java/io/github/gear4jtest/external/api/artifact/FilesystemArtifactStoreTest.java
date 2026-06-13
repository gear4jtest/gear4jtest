package io.github.gear4jtest.external.api.artifact;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilesystemArtifactStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void should_reject_path_traversal_hashes() {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When / Then
        assertThatThrownBy(() -> store.get("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.exists("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_round_trip_content() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When
        String hash = store.put("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Then
        assertThat(store.exists(hash)).isTrue();
        assertThat(new String(store.get(hash).orElseThrow().openStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void put_shouldStoreDefensiveCopyOfContent() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);
        byte[] content = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // When
        String hash = store.put(content);
        content[0] = 'j';

        // Then
        assertThat(new String(store.get(hash).orElseThrow().openStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void put_shouldStreamInputDirectlyToFilesystemStore() throws Exception {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When
        String hash = store
                .put(new java.io.ByteArrayInputStream("streamed".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                     64);

        // Then
        assertThat(store.get(hash).orElseThrow().size()).as("artifact size is captured during streaming write")
                .isEqualTo(8);
        assertThat(new String(store.get(hash).orElseThrow().openStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("streamed");
    }

    @Test
    void put_shouldRejectStreamAboveConfiguredLimitBeforePublishingTargetFile() {
        // Given
        FilesystemArtifactStore store = new FilesystemArtifactStore(tempDir);

        // When / Then
        assertThatThrownBy(() -> store
                .put(new java.io.ByteArrayInputStream("too-large".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                     3))
                .as("streaming writes must enforce max artifact size")
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("maxBytes=3");
    }
}
