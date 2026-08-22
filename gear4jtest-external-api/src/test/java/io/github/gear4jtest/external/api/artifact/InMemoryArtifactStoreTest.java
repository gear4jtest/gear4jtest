package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

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

    @Test
    void put_shouldEnforcePerArtifactTotalAndEntryLimitsWithoutRejectingDuplicates() throws Exception {
        // Given
        InMemoryArtifactStore store = new InMemoryArtifactStore(3L, 5L, 2);
        byte[] first = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] second = "de".getBytes(StandardCharsets.UTF_8);

        // When
        String firstHash = store.put(first);
        String duplicateHash = store.put(first);
        String secondHash = store.put(second);

        // Then
        assertThat(duplicateHash).isEqualTo(firstHash);
        assertThat(store.exists(secondHash)).isTrue();
        assertThatThrownBy(() -> store.put("f".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("entry limit");
        assertThatThrownBy(() -> new InMemoryArtifactStore(3L, 100L, 10)
                .put("toolarge".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("artifact byte limit");
    }

    @Test
    void streamingPut_shouldNotBypassTheConfiguredArtifactLimit() {
        // Given
        InMemoryArtifactStore store = new InMemoryArtifactStore(3L, 100L, 10);

        // When / Then
        assertThatThrownBy(() -> store.put(new ByteArrayInputStream(new byte[4]), ArtifactStore.UNLIMITED_SIZE))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("configured limit");
    }

    @Test
    void close_shouldReleaseContentAndRejectFurtherUse() throws Exception {
        // Given
        InMemoryArtifactStore store = new InMemoryArtifactStore();
        String hash = store.put("content".getBytes(StandardCharsets.UTF_8));

        // When
        store.close();
        store.close();

        // Then
        assertThatThrownBy(() -> store.exists(hash))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }
}
