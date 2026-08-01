package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class ArtifactSpoolFiles {
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
                                                                                 PosixFilePermission.OWNER_WRITE,
                                                                                 PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ,
                                                                            PosixFilePermission.OWNER_WRITE);
    private static final Set<AclEntryPermission> ACL_PERMISSIONS = Set.of(AclEntryPermission.values());
    private static final Set<AclEntryFlag> DIRECTORY_ACL_FLAGS = Set.of(AclEntryFlag.DIRECTORY_INHERIT,
                                                                        AclEntryFlag.FILE_INHERIT);

    private ArtifactSpoolFiles() {
    }

    static Path createTempFile(Path configuredDirectory, String prefix) throws IOException {
        return createTempFile(configuredDirectory, prefix, true);
    }

    static Path createTempFile(Path configuredDirectory, String prefix, boolean requirePrivatePermissions)
            throws IOException {
        return createTempFileInPreparedDirectory(prepareDirectory(configuredDirectory, requirePrivatePermissions),
                                                 prefix, requirePrivatePermissions);
    }

    static Path prepareDirectory(Path configuredDirectory, boolean requirePrivatePermissions) throws IOException {
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
        securePermissions(directory, DIRECTORY_PERMISSIONS, true, requirePrivatePermissions);
        return directory;
    }

    static Path createTempFileInPreparedDirectory(Path directory, String prefix, boolean requirePrivatePermissions)
            throws IOException {
        Path tempFile = Files.createTempFile(directory, prefix, ".tmp");
        try {
            secureFilePermissions(tempFile, requirePrivatePermissions);
            return tempFile;
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(tempFile);
            throw exception;
        }
    }

    static void secureFilePermissions(Path file, boolean requirePrivatePermissions) throws IOException {
        securePermissions(file, FILE_PERMISSIONS, false, requirePrivatePermissions);
    }

    private static void securePermissions(Path path,
                                          Set<PosixFilePermission> posixPermissions,
                                          boolean directory,
                                          boolean requirePrivatePermissions)
            throws IOException {
        PermissionAttempt posix = applyAndVerifyPosixPermissions(path, posixPermissions);
        if (posix.applied()) {
            return;
        }

        PermissionAttempt acl = applyAndVerifyOwnerOnlyAcl(path, directory);
        if (acl.applied()) {
            return;
        }

        if (requirePrivatePermissions) {
            IOException failure = new IOException("Artifact spool " + (directory ? "directory" : "file")
                    + " does not provide verifiable owner-only POSIX permissions or ACLs: " + path
                    + ". Configure a private filesystem or set requirePrivatePermissions=false only when access is "
                    + "enforced outside Gear4J.");
            if (acl.failure() != null) {
                failure.addSuppressed(acl.failure());
            }
            if (posix.failure() != null) {
                failure.addSuppressed(posix.failure());
            }
            throw failure;
        }
    }

    private static PermissionAttempt applyAndVerifyPosixPermissions(Path path,
                                                                    Set<PosixFilePermission> permissions) {
        try {
            PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class,
                                                                     LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                return PermissionAttempt.unsupported();
            }
            view.setPermissions(permissions);
            if (!view.readAttributes().permissions().equals(permissions)) {
                return PermissionAttempt.failed(
                                                new IOException(
                                                        "Owner-only POSIX permissions could not be verified for "
                                                                + path));
            }
            return PermissionAttempt.success();
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            return PermissionAttempt.failed(
                                            new IOException("Unable to apply owner-only POSIX permissions to " + path,
                                                    exception));
        }
    }

    private static PermissionAttempt applyAndVerifyOwnerOnlyAcl(Path path, boolean directory) {
        try {
            AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class,
                                                                   LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                return PermissionAttempt.unsupported();
            }
            UserPrincipal owner = view.getOwner();
            AclEntry.Builder entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(ACL_PERMISSIONS);
            if (directory) {
                entry.setFlags(DIRECTORY_ACL_FLAGS);
            }
            view.setAcl(List.of(entry.build()));
            if (!hasOwnerOnlyAcl(view.getAcl(), owner)) {
                return PermissionAttempt.failed(new IOException("Owner-only ACL could not be verified for " + path));
            }
            return PermissionAttempt.success();
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            return PermissionAttempt.failed(
                                            new IOException("Unable to apply an owner-only ACL to " + path, exception));
        }
    }

    static boolean hasOwnerOnlyAcl(List<AclEntry> entries, UserPrincipal owner) {
        Set<AclEntryPermission> ownerPermissions = EnumSet.noneOf(AclEntryPermission.class);
        for (AclEntry entry : entries) {
            if (entry.type() != AclEntryType.ALLOW) {
                continue;
            }
            if (!entry.principal().equals(owner)) {
                return false;
            }
            ownerPermissions.addAll(entry.permissions());
        }
        return ownerPermissions.containsAll(ACL_PERMISSIONS);
    }

    private record PermissionAttempt(boolean applied, IOException failure) {
        static PermissionAttempt success() {
            return new PermissionAttempt(true, null);
        }

        static PermissionAttempt unsupported() {
            return new PermissionAttempt(false, null);
        }

        static PermissionAttempt failed(IOException failure) {
            return new PermissionAttempt(false, failure);
        }
    }
}
