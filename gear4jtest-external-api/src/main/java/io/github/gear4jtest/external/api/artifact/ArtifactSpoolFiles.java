package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

final class ArtifactSpoolFiles {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
                                                                                 PosixFilePermission.OWNER_WRITE,
                                                                                 PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
                                                                            PosixFilePermission.OWNER_WRITE);

    private ArtifactSpoolFiles() {
    }

    static Path createTempFile(Path configuredDirectory, String prefix) throws IOException {
        return createTempFileInPreparedDirectory(prepareDirectory(configuredDirectory), prefix);
    }

    static Path prepareDirectory(Path configuredDirectory) throws IOException {
        Path directory = configuredDirectory != null ? configuredDirectory
                : Path.of(System.getProperty("java.io.tmpdir"), "gear4j-artifacts");
        directory = directory.toAbsolutePath().normalize();
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(directory)) {
            throw new IOException("Artifact spool directory must not be a symbolic link: " + directory);
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("Artifact spool directory must not be a symbolic link: " + directory);
        }
        setPosixPermissions(directory, DIRECTORY_PERMISSIONS);
        return directory;
    }

    static Path createTempFileInPreparedDirectory(Path directory, String prefix) throws IOException {
        Path tempFile = Files.createTempFile(directory, prefix, ".tmp");
        try {
            secureFilePermissions(tempFile);
            return tempFile;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(tempFile);
            throw exception;
        }
    }

    static void secureFilePermissions(Path file) throws IOException {
        setPosixPermissions(file, FILE_PERMISSIONS);
    }

    private static void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // POSIX permissions are unavailable on this filesystem. The platform's
            // createTempFile permissions remain in effect.
        }
    }
}
