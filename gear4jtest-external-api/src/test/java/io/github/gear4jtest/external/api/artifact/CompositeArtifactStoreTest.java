package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeArtifactStoreTest {

    @Test
    void putInputStream_shouldWritePrimaryAndSynchronousFallbacksWithoutUsingByteArrayPut() throws IOException {
        // Given
        StreamOnlyArtifactStore primary = new StreamOnlyArtifactStore();
        StreamOnlyArtifactStore fallback = new StreamOnlyArtifactStore();
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(fallback),
                CompositeArtifactStore.WriteMode.SYNC_ALL, CompositeArtifactStore.ReadMode.PREFER_PRIMARY, false,
                false, Runnable::run);

        // When
        String hash = store.put(new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)), 16L);

        // Then
        assertThat(primary.streamWrites()).isEqualTo(1);
        assertThat(fallback.streamWrites()).isEqualTo(1);
        assertThat(primary.storedHashes()).containsExactly(hash);
        assertThat(fallback.storedHashes()).containsExactly(hash);
    }

    @Test
    void get_shouldUseConfiguredVerificationLimitInsteadOfDefaultOnly() throws IOException {
        // Given
        InMemoryArtifactStore primary = new InMemoryArtifactStore();
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);
        String hash = primary.put(content);
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(),
                CompositeArtifactStore.WriteMode.PRIMARY_ONLY, CompositeArtifactStore.ReadMode.PREFER_PRIMARY, true,
                false, content.length, Runnable::run);

        // When
        Optional<Artifact> artifact = store.get(hash);

        // Then
        assertThat(artifact).isPresent();
        assertThat(new String(artifact.orElseThrow().openStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("payload");
    }

    @Test
    void get_shouldRejectArtifactsAboveConfiguredVerificationLimit() {
        // Given
        InMemoryArtifactStore primary = new InMemoryArtifactStore();
        byte[] content = "payload".getBytes(StandardCharsets.UTF_8);
        String hash = primary.put(content);
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(),
                CompositeArtifactStore.WriteMode.PRIMARY_ONLY, CompositeArtifactStore.ReadMode.PREFER_PRIMARY, true,
                false, content.length - 1L, Runnable::run);

        // When / Then
        assertThatThrownBy(() -> store.get(hash))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=" + (content.length - 1L));
    }

    private static final class StreamOnlyArtifactStore implements ArtifactStore {
        private final List<String> storedHashes = new ArrayList<>();
        private int streamWrites;

        @Override
        public String put(byte[] content) {
            throw new AssertionError("CompositeArtifactStore should use streaming writes");
        }

        @Override
        public String put(InputStream in, long maxBytes) throws IOException {
            streamWrites++;
            String hash = ArtifactHashes.sha256Hex(in, maxBytes).hashHex();
            storedHashes.add(hash);
            return hash;
        }

        @Override
        public Optional<Artifact> get(String hashHex) throws IOException {
            return Optional.empty();
        }

        @Override
        public boolean exists(String hashHex) throws IOException {
            return storedHashes.contains(hashHex);
        }

        private List<String> storedHashes() {
            return storedHashes;
        }

        private int streamWrites() {
            return streamWrites;
        }
    }
}
