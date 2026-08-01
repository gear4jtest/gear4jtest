package io.github.gear4jtest.external.api.artifact;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
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
            } else {
                AclFileAttributeView directoryAcl = Files.getFileAttributeView(spoolDirectory,
                                                                               AclFileAttributeView.class);
                AclFileAttributeView fileAcl = Files.getFileAttributeView(tempFile, AclFileAttributeView.class);
                assertThat(directoryAcl).isNotNull();
                assertThat(fileAcl).isNotNull();
                assertThat(ArtifactSpoolFiles.hasOwnerOnlyAcl(directoryAcl.getAcl(), directoryAcl.getOwner()))
                        .isTrue();
                assertThat(ArtifactSpoolFiles.hasOwnerOnlyAcl(fileAcl.getAcl(), fileAcl.getOwner())).isTrue();
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

    @Test
    void createTempFile_shouldFailClosedWhenPrivatePermissionsCannotBeVerified() throws Exception {
        // Given
        URI archive = URI.create("jar:" + tempDirectory.resolve("basic-only.zip").toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(archive, Map.of("create", "true"))) {
            Path spoolDirectory = fileSystem.getPath("/private-spool");

            // When / Then
            assertThatThrownBy(() -> ArtifactSpoolFiles.createTempFile(spoolDirectory, "artifact-", true))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("verifiable owner-only")
                    .hasMessageContaining("requirePrivatePermissions=false");

            Path explicitlyManagedFile = ArtifactSpoolFiles.createTempFile(spoolDirectory, "artifact-", false);
            assertThat(explicitlyManagedFile).isRegularFile();
            Files.delete(explicitlyManagedFile);
        }
    }

    @Test
    void hasOwnerOnlyAcl_shouldRejectAnAllowEntryForAnotherPrincipal() {
        // Given
        UserPrincipal owner = () -> "owner";
        UserPrincipal other = () -> "other";
        AclEntry ownerAllow = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(Set.of(AclEntryPermission.values()))
                .build();
        AclEntry otherDeny = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(other)
                .setPermissions(AclEntryPermission.READ_DATA)
                .build();
        AclEntry otherAllow = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(other)
                .setPermissions(AclEntryPermission.READ_DATA)
                .build();

        // When / Then
        assertThat(ArtifactSpoolFiles.hasOwnerOnlyAcl(List.of(ownerAllow, otherDeny), owner)).isTrue();
        assertThat(ArtifactSpoolFiles.hasOwnerOnlyAcl(List.of(ownerAllow, otherAllow), owner)).isFalse();
        assertThat(ArtifactSpoolFiles.hasOwnerOnlyAcl(List.of(otherDeny), owner)).isFalse();
    }
}
