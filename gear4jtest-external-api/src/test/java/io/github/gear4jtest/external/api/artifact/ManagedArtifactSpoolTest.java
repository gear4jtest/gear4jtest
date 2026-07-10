package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedArtifactSpoolTest {
    @TempDir
    Path tempDirectory;

    @Test
    void initialize_shouldDeleteStaleFilesAndExposeCleanupStats() throws Exception {
        // Given
        byte[] secret = "fixture-secret-must-not-leak".getBytes(StandardCharsets.UTF_8);
        Path staleFile = Files.createTempFile(tempDirectory, "abandoned-", ".tmp");
        Files.write(staleFile, secret);
        Files.setLastModifiedTime(staleFile, FileTime.from(Instant.now().minus(Duration.ofHours(2))));

        // When
        ManagedArtifactSpool spool = new ManagedArtifactSpool(ArtifactSpoolPolicy.builder()
                .directory(tempDirectory)
                .maxBytes(1024)
                .staleFileAge(Duration.ofHours(1))
                .build());

        // Then
        assertThat(staleFile).doesNotExist();
        ArtifactSpoolStats stats = spool.snapshotStats();
        assertThat(stats.currentFiles()).isZero();
        assertThat(stats.currentBytes()).isZero();
        assertThat(stats.staleFilesDeleted()).isEqualTo(1L);
        assertThat(stats.staleBytesDeleted()).isEqualTo(secret.length);
    }

    @Test
    void output_shouldRejectQuotaBeforeWritingAndReleaseFileOnCleanup() throws Exception {
        // Given
        ManagedArtifactSpool spool = new ManagedArtifactSpool(ArtifactSpoolPolicy.builder()
                .directory(tempDirectory)
                .maxBytes(3)
                .build());
        Path file = spool.createTempFile("quota-");

        try {
            // When / Then
            assertThatThrownBy(() -> {
                try (var output = spool.openOutput(file)) {
                    output.write(new byte[4]);
                }
            }).isInstanceOf(IOException.class)
                    .hasMessageContaining("quota exceeded")
                    .hasMessageNotContaining("fixture-secret");
            assertThat(Files.size(file)).isZero();
            assertThat(spool.snapshotStats().quotaRejections()).isEqualTo(1L);
        } finally {
            spool.delete(file);
        }

        assertThat(spool.snapshotStats().currentFiles()).isZero();
        assertThat(spool.snapshotStats().currentBytes()).isZero();
    }

    @Test
    void initialize_shouldExposeRecentResidualOccupancy() throws Exception {
        // Given
        Path residualFile = Files.createTempFile(tempDirectory, "recent-", ".tmp");
        Files.write(residualFile, new byte[7]);

        // When
        ManagedArtifactSpool spool = new ManagedArtifactSpool(ArtifactSpoolPolicy.builder()
                .directory(tempDirectory)
                .maxBytes(10)
                .staleFileAge(Duration.ofHours(1))
                .build());

        // Then
        assertThat(spool.snapshotStats().currentFiles()).isEqualTo(1L);
        assertThat(spool.snapshotStats().currentBytes()).isEqualTo(7L);
        spool.delete(residualFile);
    }
}
