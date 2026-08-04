package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactHashesTest {
    @Test
    void requireContentIdentity_shouldValidateSizeAndDigest() {
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        String hash = ArtifactHashes.sha256Hex(content);

        assertThatCode(() -> ArtifactHashes.requireContentIdentity(content, hash, content.length, "artifact"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ArtifactHashes.requireContentIdentity(content, hash, content.length + 1L,
                                                                       "artifact"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("size mismatch");
        assertThatThrownBy(() -> ArtifactHashes.requireContentIdentity(content, "0".repeat(64), content.length,
                                                                       "artifact"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("content hash mismatch");
    }

    @Test
    void requireSha256Match_shouldNormalizeValidHashesAndRejectInvalidMetadata() {
        String hash = ArtifactHashes.sha256Hex("content".getBytes(StandardCharsets.UTF_8));

        assertThatCode(() -> ArtifactHashes.requireSha256Match(hash, hash.toUpperCase(java.util.Locale.ROOT),
                                                               "artifact metadata"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> ArtifactHashes.requireSha256Match(hash, "invalid", "artifact metadata"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("invalid SHA-256");
    }
}
