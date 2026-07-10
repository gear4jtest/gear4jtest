package io.github.gear4jtest.external.api.artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactSpoolFilesTest {
    @TempDir
    Path tempDirectory;

    @Test
    void createTempFile_shouldUseConfiguredPrivateDirectory() throws Exception {
        // Given
        Path spoolDirectory = tempDirectory.resolve("private-spool");

        // When
        Path tempFile = ArtifactSpoolFiles.createTempFile(spoolDirectory, "artifact-");

        try {
            // Then
            assertThat(tempFile.getParent()).isEqualTo(spoolDirectory);
            assertThat(Files.isRegularFile(tempFile)).isTrue();
            if (Files.getFileStore(tempFile).supportsFileAttributeView("posix")) {
                assertThat(Files.getPosixFilePermissions(spoolDirectory))
                        .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                          PosixFilePermission.OWNER_EXECUTE));
                assertThat(Files.getPosixFilePermissions(tempFile))
                        .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void createTempFile_shouldRejectSymbolicLinkDirectory() throws Exception {
        // Given
        Path target = Files.createDirectory(tempDirectory.resolve("target"));
        Path link = tempDirectory.resolve("spool-link");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
        }

        // When / Then
        assertThatThrownBy(() -> ArtifactSpoolFiles.createTempFile(link, "artifact-"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("symbolic link");
    }
}
