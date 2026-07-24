package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

final class SecureArtifactFiles {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
                                                                                 PosixFilePermission.OWNER_WRITE,
                                                                                 PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
                                                                            PosixFilePermission.OWNER_WRITE);

    private SecureArtifactFiles() {
    }

    static Path prepareRoot(Path configuredRoot) throws IOException {
        Path root = configuredRoot.toAbsolutePath().normalize();
        createDirectoriesWithoutFollowingLinks(root);
        requireSecureRoot(root);
        return root;
    }

    static void requireSecureRoot(Path root) throws IOException {
        requireDirectoryChain(root);
        secureDirectoryPermissions(root);
    }

    static Path createPrivateTempFile(Path root) throws IOException {
        requireSecureRoot(root);
        Path tempFile;
        if (supportsPosix(root)) {
            tempFile = Files.createTempFile(root, "artifact-", ".tmp",
                                            PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
        } else {
            tempFile = Files.createTempFile(root, "artifact-", ".tmp");
        }
        try {
            requireRegularFile(tempFile);
            secureFilePermissions(tempFile);
            requireSecureRoot(root);
            return tempFile;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(tempFile);
            throw exception;
        }
    }

    static Path prepareArtifactParent(Path root, String firstSegment, String secondSegment) throws IOException {
        requireSecureRoot(root);
        Path first = createPrivateDirectory(root.resolve(firstSegment));
        Path second = createPrivateDirectory(first.resolve(secondSegment));
        requireSecureRoot(root);
        requireDirectory(first);
        requireDirectory(second);
        return second;
    }

    static boolean requireExistingArtifactParent(Path root, Path parent) throws IOException {
        requireSecureRoot(root);
        Path relative = root.relativize(parent);
        Path current = root;
        for (Path segment : relative) {
            current = current.resolve(segment);
            BasicFileAttributes attributes = readAttributesIfPresent(current);
            if (attributes == null) {
                return false;
            }
            requireDirectory(current, attributes);
            secureDirectoryPermissions(current);
        }
        requireSecureRoot(root);
        return true;
    }

    static BasicFileAttributes readRegularFileAttributesIfPresent(Path file) throws IOException {
        BasicFileAttributes attributes = readAttributesIfPresent(file);
        if (attributes == null) {
            return null;
        }
        if (attributes.isSymbolicLink()) {
            throw new IOException("Filesystem artifact must not be a symbolic link: " + file);
        }
        if (!attributes.isRegularFile()) {
            throw new IOException("Filesystem artifact must be a regular file: " + file);
        }
        return attributes;
    }

    static void secureFilePermissions(Path file) throws IOException {
        BasicFileAttributes attributes = readRegularFileAttributesIfPresent(file);
        if (attributes == null) {
            throw new NoSuchFileException(file.toString());
        }
        setPosixPermissions(file, FILE_PERMISSIONS);
    }

    private static void createDirectoriesWithoutFollowingLinks(Path directory) throws IOException {
        Path current = directory.getRoot();
        if (current == null) {
            throw new IOException("Filesystem artifact root must be absolute: " + directory);
        }
        for (Path segment : directory) {
            current = current.resolve(segment);
            BasicFileAttributes attributes = readAttributesIfPresent(current);
            if (attributes != null) {
                requireDirectory(current, attributes);
                continue;
            }
            createPrivateDirectory(current);
        }
    }

    private static Path createPrivateDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = readAttributesIfPresent(directory);
        if (attributes == null) {
            try {
                Path parent = directory.getParent();
                if (parent != null && supportsPosix(parent)) {
                    Files.createDirectory(directory,
                                          PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
                } else {
                    Files.createDirectory(directory);
                }
            } catch (FileAlreadyExistsException exception) {
                // A concurrent creator is acceptable only if it created the expected
                // non-symbolic directory. The validation below is authoritative.
            }
        }
        requireDirectory(directory);
        secureDirectoryPermissions(directory);
        return directory;
    }

    private static void requireDirectoryChain(Path directory) throws IOException {
        Path current = directory.getRoot();
        if (current == null) {
            throw new IOException("Filesystem artifact root must be absolute: " + directory);
        }
        for (Path segment : directory) {
            current = current.resolve(segment);
            requireDirectory(current);
        }
    }

    private static void requireDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = readAttributesIfPresent(directory);
        if (attributes == null) {
            throw new NoSuchFileException(directory.toString());
        }
        requireDirectory(directory, attributes);
    }

    private static void requireDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
        if (attributes.isSymbolicLink()) {
            throw new IOException("Filesystem artifact directory must not be a symbolic link: " + directory);
        }
        if (!attributes.isDirectory()) {
            throw new IOException("Filesystem artifact path must be a directory: " + directory);
        }
    }

    private static void requireRegularFile(Path file) throws IOException {
        if (readRegularFileAttributesIfPresent(file) == null) {
            throw new NoSuchFileException(file.toString());
        }
    }

    private static BasicFileAttributes readAttributesIfPresent(Path path) throws IOException {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException exception) {
            return null;
        }
    }

    private static void secureDirectoryPermissions(Path directory) throws IOException {
        setPosixPermissions(directory, DIRECTORY_PERMISSIONS);
    }

    private static void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class,
                                                                 LinkOption.NOFOLLOW_LINKS);
        if (view != null) {
            view.setPermissions(permissions);
        }
    }

    private static boolean supportsPosix(Path path) throws IOException {
        return Files.getFileStore(path).supportsFileAttributeView("posix");
    }
}
