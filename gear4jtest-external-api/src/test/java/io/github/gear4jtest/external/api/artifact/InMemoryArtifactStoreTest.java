package io.github.gear4jtest.external.api.artifact;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryArtifactStoreTest {
    @Test
    void should_defensively_copy_content_on_put_and_get() throws Exception {
        // Given
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        byte[] content = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // When
        String hash = store.put(content);
        content[0] = 'H';

        // Then
        byte[] stored = store.get(hash).orElseThrow().openStream().readAllBytes();
        assertThat(new String(stored, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("hello");

        stored[0] = 'H';
        byte[] storedAgain = store.get(hash).orElseThrow().openStream().readAllBytes();
        assertThat(new String(storedAgain, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void should_reject_invalid_hashes() {
        // Given
        InMemoryArtifactStore store = new InMemoryArtifactStore();

        // When / Then
        assertThatThrownBy(() -> store.exists("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.get("abc")).isInstanceOf(IllegalArgumentException.class);
    }
}
