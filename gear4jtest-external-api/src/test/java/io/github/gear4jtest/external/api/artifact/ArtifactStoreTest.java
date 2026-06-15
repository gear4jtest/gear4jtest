package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactStoreTest {
    @Test
    void readAllBytes_shouldReadWhenSizeIsWithinLimit() throws IOException {
        // Given
        byte[] input = "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // When
        byte[] result = ArtifactStore.readAllBytes(new ByteArrayInputStream(input), 3);

        // Then
        assertThat(result).as("bytes within the configured limit are returned").isEqualTo(input);
    }

    @Test
    void readAllBytes_shouldFailWhenSizeExceedsLimit() {
        // Given
        byte[] input = "abcd".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // When / Then
        assertThatThrownBy(() -> ArtifactStore.readAllBytes(new ByteArrayInputStream(input), 3))
                .as("artifact bytes above the configured limit must be rejected")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=3");
    }

    @Test
    void put_shouldRejectInputStreamAboveDefaultLimit() {
        // Given
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        byte[] input = new byte[(int) ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES + 1];

        // When / Then
        assertThatThrownBy(() -> store.put(new ByteArrayInputStream(input)))
                .as("default InputStream writes should be bounded")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=" + ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
    }

    @Test
    void put_shouldSupportInputStreamWithExplicitLimit() throws IOException {
        // Given
        InMemoryArtifactStore store = new InMemoryArtifactStore();

        // When
        String hash = store.put(new ByteArrayInputStream("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)), 3);

        // Then
        assertThat(store.exists(hash)).as("the artifact is stored through the limited InputStream API").isTrue();
    }
}
