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
    void policy_shouldRequirePrivatePermissionsByDefaultAndPreserveExplicitOptOut() {
        // Given
        ArtifactSpoolPolicy defaults = ArtifactSpoolPolicy.defaults();

        // When
        ArtifactSpoolPolicy explicitlyManaged = defaults.toBuilder()
                .requirePrivatePermissions(false)
                .build();

        // Then
        assertThat(defaults.requirePrivatePermissions()).isTrue();
        assertThat(explicitlyManaged.requirePrivatePermissions()).isFalse();
    }

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
        try {
            // Then
            assertThat(staleFile).doesNotExist();
            ArtifactSpoolStats stats = spool.snapshotStats();
            assertThat(stats.currentFiles()).isZero();
            assertThat(stats.currentBytes()).isZero();
            assertThat(stats.staleFilesDeleted()).isEqualTo(1L);
            assertThat(stats.staleBytesDeleted()).isEqualTo(secret.length);
        } finally {
            spool.close();
        }
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
            try (var output = spool.openOutput(file)) {
                assertThatThrownBy(() -> output.write(new byte[4]))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("quota exceeded")
                        .hasMessageNotContaining("fixture-secret");
            }
            assertThat(Files.size(file)).isZero();
            assertThat(spool.snapshotStats().quotaRejections()).isEqualTo(1L);
        } finally {
            spool.delete(file);
            spool.close();
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
        spool.close();
    }

    @Test
    void restart_shouldAccountForCrashResidueThenDeleteItAfterRetention() throws Exception {
        // Given: the first process writes a spool file and terminates before cleanup.
        byte[] pendingCopy = "pending-fallback-copy".getBytes(StandardCharsets.UTF_8);
        ArtifactSpoolPolicy policy = ArtifactSpoolPolicy.builder()
                .directory(tempDirectory)
                .maxBytes(pendingCopy.length)
                .staleFileAge(Duration.ofHours(1))
                .build();
        ManagedArtifactSpool beforeCrash = new ManagedArtifactSpool(policy);
        Path abandonedFile = beforeCrash.createTempFile("async-");
        try (var output = beforeCrash.openOutput(abandonedFile)) {
            output.write(pendingCopy);
        }
        beforeCrash.close();

        // When: a new process initializes the same spool before the retention age.
        ManagedArtifactSpool immediateRestart = new ManagedArtifactSpool(policy);

        // Then: the residue is never replayed or hidden from the quota.
        assertThat(immediateRestart.snapshotStats().currentFiles()).isEqualTo(1L);
        assertThat(immediateRestart.snapshotStats().currentBytes()).isEqualTo(pendingCopy.length);
        assertThat(abandonedFile).exists();
        Path rejectedFile = immediateRestart.createTempFile("quota-after-restart-");
        try {
            try (var output = immediateRestart.openOutput(rejectedFile)) {
                assertThatThrownBy(() -> output.write(1))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("quota exceeded");
            }
        } finally {
            immediateRestart.delete(rejectedFile);
        }
        assertThat(immediateRestart.snapshotStats().quotaRejections()).isEqualTo(1L);
        assertThat(immediateRestart.snapshotStats().currentFiles()).isEqualTo(1L);

        // When: a later restart occurs after the configured retention age.
        Files.setLastModifiedTime(abandonedFile, FileTime.from(Instant.now().minus(Duration.ofHours(2))));
        ManagedArtifactSpool afterRetentionRestart = new ManagedArtifactSpool(policy);

        // Then
        assertThat(abandonedFile).doesNotExist();
        assertThat(afterRetentionRestart.snapshotStats().currentFiles()).isZero();
        assertThat(afterRetentionRestart.snapshotStats().currentBytes()).isZero();
        assertThat(afterRetentionRestart.snapshotStats().staleFilesDeleted()).isEqualTo(1L);
        assertThat(afterRetentionRestart.snapshotStats().staleBytesDeleted()).isEqualTo(pendingCopy.length);
        immediateRestart.close();
        afterRetentionRestart.close();
    }

    @Test
    void instancesSharingADirectory_shouldEnforceOneGlobalQuota() throws Exception {
        // Given
        ArtifactSpoolPolicy policy = ArtifactSpoolPolicy.builder()
                .directory(tempDirectory)
                .maxBytes(5L)
                .build();
        ManagedArtifactSpool first = new ManagedArtifactSpool(policy);
        ManagedArtifactSpool second = new ManagedArtifactSpool(policy);
        Path firstFile = first.createTempFile("first-");
        Path secondFile = second.createTempFile("second-");

        try {
            // When
            try (var output = first.openOutput(firstFile)) {
                output.write(new byte[3]);
            }

            // Then
            try (var output = second.openOutput(secondFile)) {
                assertThatThrownBy(() -> output.write(new byte[3]))
                        .isInstanceOf(IOException.class)
                        .hasMessageContaining("currentBytes=3");
            }
            assertThat(first.snapshotStats().currentBytes()).isEqualTo(3L);
            assertThat(second.snapshotStats().quotaRejections()).isEqualTo(1L);
        } finally {
            first.delete(firstFile);
            second.delete(secondFile);
            first.close();
            second.close();
        }
    }

    @Test
    void instancesSharingADirectory_shouldRejectIncompatiblePolicies() throws Exception {
        // Given
        ManagedArtifactSpool first = new ManagedArtifactSpool(ArtifactSpoolPolicy.builder()
                .directory(tempDirectory)
                .maxBytes(5L)
                .build());

        try {
            // When / Then
            assertThatThrownBy(() -> new ManagedArtifactSpool(ArtifactSpoolPolicy.builder()
                    .directory(tempDirectory)
                    .maxBytes(6L)
                    .build()))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("different policy");
        } finally {
            first.close();
        }
    }

    @Test
    void close_shouldRejectNewFilesAndRemainIdempotent() throws Exception {
        // Given
        ManagedArtifactSpool spool = new ManagedArtifactSpool(ArtifactSpoolPolicy.builder()
                .directory(tempDirectory)
                .build());

        // When
        spool.close();
        spool.close();

        // Then
        assertThatThrownBy(() -> spool.createTempFile("closed-"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("closed");
    }
}
