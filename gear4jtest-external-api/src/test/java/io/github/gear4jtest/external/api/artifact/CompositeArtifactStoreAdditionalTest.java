package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeArtifactStoreAdditionalTest {
    @Test
    void constructor_shouldUseDefaultsForNullModesAndExecutor() throws IOException {
        InMemoryArtifactStore primary = new InMemoryArtifactStore();
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(), null, null, false, false, null);

        String hash = store.put("payload".getBytes(StandardCharsets.UTF_8));

        assertThat(primary.exists(hash)).isTrue();
        assertThat(store.exists(hash)).isTrue();
    }

    @Test
    void constructor_shouldRejectInvalidVerificationLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CompositeArtifactStore(new InMemoryArtifactStore(), List.of(),
                        CompositeArtifactStore.WriteMode.PRIMARY_ONLY, CompositeArtifactStore.ReadMode.PREFER_PRIMARY,
                        true, false, 0L, Runnable::run))
                .withMessageContaining("verificationMaxArtifactSizeBytes");
    }

    @Test
    void asyncFallbacks_shouldWriteByteArrayContentUsingConfiguredExecutor() throws IOException {
        InMemoryArtifactStore primary = new InMemoryArtifactStore();
        InMemoryArtifactStore fallback = new InMemoryArtifactStore();
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(fallback),
                CompositeArtifactStore.WriteMode.ASYNC_FALLBACKS, CompositeArtifactStore.ReadMode.PREFER_PRIMARY,
                false, false, Runnable::run);

        String hash = store.put("payload".getBytes(StandardCharsets.UTF_8));

        assertThat(primary.exists(hash)).isTrue();
        assertThat(fallback.exists(hash)).isTrue();
    }

    @Test
    void get_shouldReadFromFallbackAndSelfHealPrimaryWhenEnabled() throws IOException {
        InMemoryArtifactStore primary = new InMemoryArtifactStore();
        InMemoryArtifactStore fallback = new InMemoryArtifactStore();
        String hash = fallback.put("payload".getBytes(StandardCharsets.UTF_8));
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(fallback),
                CompositeArtifactStore.WriteMode.PRIMARY_ONLY, CompositeArtifactStore.ReadMode.PREFER_PRIMARY,
                true, true, Runnable::run);

        Artifact artifact = store.get(hash).orElseThrow();

        assertThat(new String(artifact.openStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("payload");
        assertThat(primary.exists(hash)).isTrue();
    }

    @Test
    void get_shouldRejectCorruptFallbackBeforeHealingPrimary() throws IOException {
        InMemoryArtifactStore primary = new InMemoryArtifactStore();
        InMemoryArtifactStore fallback = new InMemoryArtifactStore();
        String expectedHash = Hashing.sha256Hex("expected".getBytes(StandardCharsets.UTF_8));
        String actualHash = fallback.put("corrupt".getBytes(StandardCharsets.UTF_8));
        assertThat(actualHash).isNotEqualTo(expectedHash);
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(new AliasArtifactStore(fallback,
                expectedHash, actualHash)), CompositeArtifactStore.WriteMode.PRIMARY_ONLY,
                CompositeArtifactStore.ReadMode.PREFER_PRIMARY, true, true, Runnable::run);

        assertThatThrownBy(() -> store.get(expectedHash))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Corrupt artifact");
        assertThat(primary.exists(expectedHash)).isFalse();
    }

    @Test
    void exists_shouldCheckFallbacksWhenPrimaryMisses() throws IOException {
        InMemoryArtifactStore primary = new InMemoryArtifactStore();
        InMemoryArtifactStore fallback = new InMemoryArtifactStore();
        String hash = fallback.put("payload".getBytes(StandardCharsets.UTF_8));
        CompositeArtifactStore store = new CompositeArtifactStore(primary, List.of(fallback),
                CompositeArtifactStore.WriteMode.PRIMARY_ONLY, CompositeArtifactStore.ReadMode.PREFER_PRIMARY,
                false, false, Runnable::run);

        assertThat(store.exists(hash)).isTrue();
        assertThat(store.get(Hashing.sha256Hex("missing".getBytes(StandardCharsets.UTF_8)))).isEmpty();
    }

    private record AliasArtifactStore(InMemoryArtifactStore delegate, String aliasHash, String storedHash)
            implements ArtifactStore {
        @Override
        public String put(byte[] content) throws java.io.IOException {
            return delegate.put(content);
        }

        @Override
        public java.util.Optional<Artifact> get(String hashHex) throws java.io.IOException {
            if (aliasHash.equals(hashHex)) {
                return delegate.get(storedHash);
            }
            return delegate.get(hashHex);
        }

        @Override
        public boolean exists(String hashHex) throws java.io.IOException {
            return aliasHash.equals(hashHex) || delegate.exists(hashHex);
        }
    }
}
