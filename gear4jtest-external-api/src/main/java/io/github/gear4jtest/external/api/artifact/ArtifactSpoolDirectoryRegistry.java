package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ArtifactSpoolDirectoryRegistry {
    private static final String LOCK_FILE_NAME = ".gear4j-spool.lock";
    private static final Map<Path, SharedDirectory> DIRECTORIES = new HashMap<>();

    private ArtifactSpoolDirectoryRegistry() {
    }

    static synchronized Lease acquire(ArtifactSpoolPolicy policy) throws IOException {
        Path prepared = ArtifactSpoolFiles.prepareDirectory(policy.directory(), policy.requirePrivatePermissions());
        Path directory = prepared.toRealPath(LinkOption.NOFOLLOW_LINKS);
        SharedDirectory shared = DIRECTORIES.get(directory);
        if (shared == null) {
            shared = SharedDirectory.open(directory, policy);
            DIRECTORIES.put(directory, shared);
        } else {
            shared.requireCompatible(policy);
            shared.reconcileAndCleanup();
        }
        return new Lease(shared, shared.retainOwner());
    }

    static synchronized void release(Lease lease) {
        if (lease == null) {
            return;
        }
        lease.shared().releaseOwner(lease.ownerId());
        retireIfUnused(lease.shared());
    }

    static synchronized void retireIfUnused(SharedDirectory shared) {
        if (shared.isUnused() && DIRECTORIES.remove(shared.directory(), shared)) {
            shared.closeLock();
        }
    }

    record Lease(SharedDirectory shared, long ownerId) {}

    static final class SharedDirectory {
        private final Path directory;
        private final long maxBytes;
        private final java.time.Duration staleFileAge;
        private final boolean requirePrivatePermissions;
        private final FileChannel lockChannel;
        private final FileLock directoryLock;
        private final Map<Path, Long> sizesByFile = new HashMap<>();
        private final Map<Path, Long> ownersByFile = new HashMap<>();
        private final Set<Path> openFiles = new HashSet<>();
        private final Set<Long> owners = new HashSet<>();
        private long nextOwnerId;
        private long currentBytes;
        private long staleFilesDeleted;
        private long staleBytesDeleted;
        private long quotaRejections;
        private long cleanupFailures;

        private SharedDirectory(Path directory,
                                ArtifactSpoolPolicy policy,
                                FileChannel lockChannel,
                                FileLock directoryLock) {
            this.directory = directory;
            this.maxBytes = policy.maxBytes();
            this.staleFileAge = policy.staleFileAge();
            this.requirePrivatePermissions = policy.requirePrivatePermissions();
            this.lockChannel = lockChannel;
            this.directoryLock = directoryLock;
        }

        private static SharedDirectory open(Path directory, ArtifactSpoolPolicy policy) throws IOException {
            Path lockFile = directory.resolve(LOCK_FILE_NAME);
            if (Files.exists(lockFile, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(lockFile)) {
                throw new IOException("Artifact spool lock file must not be a symbolic link: " + lockFile);
            }
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = null;
            try {
                ArtifactSpoolFiles.secureFilePermissions(lockFile, policy.requirePrivatePermissions());
                try {
                    lock = channel.tryLock();
                } catch (OverlappingFileLockException exception) {
                    throw new IOException("Artifact spool directory is already owned by this process: " + directory,
                            exception);
                }
                if (lock == null) {
                    throw new IOException("Artifact spool directory is already used by another process: " + directory
                            + ". Configure a dedicated spoolDirectory per process or container.");
                }
                SharedDirectory shared = new SharedDirectory(directory, policy, channel, lock);
                shared.reconcileAndCleanup();
                return shared;
            } catch (IOException | RuntimeException exception) {
                closeQuietly(lock);
                closeQuietly(channel);
                throw exception;
            }
        }

        synchronized Path createTempFile(long ownerId, String prefix) throws IOException {
            requireOwner(ownerId);
            Path file = ArtifactSpoolFiles.createTempFileInPreparedDirectory(directory, prefix,
                                                                             requirePrivatePermissions);
            sizesByFile.put(file, 0L);
            ownersByFile.put(file, ownerId);
            return file;
        }

        synchronized OutputStream openOutput(long ownerId, Path file) throws IOException {
            requireOwner(ownerId);
            Path owned = requireOwnedFile(file);
            if (!Long.valueOf(ownerId).equals(ownersByFile.get(owned))) {
                throw new IOException("Artifact spool file is not owned by this spool instance");
            }
            if (!openFiles.add(owned)) {
                throw new IOException("Artifact spool file already has an open output stream");
            }
            try {
                OutputStream output = Files.newOutputStream(owned);
                reconcileSize(owned);
                return output;
            } catch (IOException | RuntimeException exception) {
                openFiles.remove(owned);
                throw exception;
            }
        }

        synchronized void reserve(Path file, long bytes) throws IOException {
            Path owned = requireOwnedFile(file);
            if (!openFiles.contains(owned)) {
                throw new IOException("Artifact spool output stream is not registered as open");
            }
            long nextSize = addSaturated(currentBytes, bytes);
            if (maxBytes >= 0 && nextSize > maxBytes) {
                quotaRejections++;
                throw new IOException("Artifact spool quota exceeded. maxBytes=" + maxBytes
                        + ", currentBytes=" + currentBytes + ", requestedBytes=" + bytes);
            }
            sizesByFile.put(owned, addSaturated(sizesByFile.getOrDefault(owned, 0L), bytes));
            currentBytes = nextSize;
        }

        synchronized void reconcileAfterWrite(Path file) {
            try {
                reconcileSize(requireOwnedFile(file));
            } catch (IOException | RuntimeException exception) {
                cleanupFailures++;
            }
        }

        synchronized void finishOutput(Path file) {
            Path normalized = file.toAbsolutePath().normalize();
            openFiles.remove(normalized);
            reconcileAfterWrite(normalized);
        }

        synchronized void delete(Path file) throws IOException {
            Path owned = requireOwnedFile(file);
            long accountedSize = sizesByFile.getOrDefault(owned, safeSize(owned));
            boolean deleted = Files.deleteIfExists(owned);
            if (deleted || !Files.exists(owned, LinkOption.NOFOLLOW_LINKS)) {
                sizesByFile.remove(owned);
                ownersByFile.remove(owned);
                openFiles.remove(owned);
                currentBytes = Math.max(0L, currentBytes - accountedSize);
            }
        }

        synchronized ArtifactSpoolStats snapshotStats() {
            return new ArtifactSpoolStats(sizesByFile.size(), currentBytes, maxBytes, staleFilesDeleted,
                    staleBytesDeleted, quotaRejections, cleanupFailures);
        }

        synchronized void recordCleanupFailure() {
            cleanupFailures++;
        }

        synchronized long retainOwner() {
            long ownerId = ++nextOwnerId;
            owners.add(ownerId);
            return ownerId;
        }

        synchronized void releaseOwner(long ownerId) {
            if (!owners.remove(ownerId)) {
                return;
            }
            ownersByFile.entrySet().removeIf(entry -> entry.getValue().equals(ownerId));
        }

        synchronized boolean isUnused() {
            return owners.isEmpty() && openFiles.isEmpty();
        }

        Path directory() {
            return directory;
        }

        private synchronized void requireCompatible(ArtifactSpoolPolicy policy) throws IOException {
            if (maxBytes != policy.maxBytes()
                    || !staleFileAge.equals(policy.staleFileAge())
                    || requirePrivatePermissions != policy.requirePrivatePermissions()) {
                throw new IOException("Artifact spool directory is already registered with a different policy: "
                        + directory + ". All stores sharing a directory must use the same maxBytes, staleFileAge "
                        + "and requirePrivatePermissions values.");
            }
        }

        private synchronized void reconcileAndCleanup() throws IOException {
            Instant cutoff = Instant.now().minus(staleFileAge);
            List<Path> files;
            try (var entries = Files.list(directory)) {
                files = entries.filter(SharedDirectory::isSpoolFile).toList();
            }
            Map<Path, Long> actualSizes = new HashMap<>();
            for (Path file : files) {
                ArtifactSpoolFiles.secureFilePermissions(file, requirePrivatePermissions);
                long size = safeSize(file);
                try {
                    FileTime lastModified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
                    if (!ownersByFile.containsKey(file)
                            && !openFiles.contains(file)
                            && lastModified.toInstant().isBefore(cutoff)
                            && Files.deleteIfExists(file)) {
                        staleFilesDeleted++;
                        staleBytesDeleted = addSaturated(staleBytesDeleted, size);
                        continue;
                    }
                } catch (IOException exception) {
                    cleanupFailures++;
                }
                actualSizes.put(file, size);
            }
            sizesByFile.clear();
            sizesByFile.putAll(actualSizes);
            ownersByFile.keySet().retainAll(actualSizes.keySet());
            openFiles.retainAll(actualSizes.keySet());
            currentBytes = 0L;
            for (long size : actualSizes.values()) {
                currentBytes = addSaturated(currentBytes, size);
            }
        }

        private void reconcileSize(Path file) throws IOException {
            long previous = sizesByFile.getOrDefault(file, 0L);
            boolean exists = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
            long actual = exists ? Files.size(file) : 0L;
            if (exists) {
                sizesByFile.put(file, actual);
            } else {
                sizesByFile.remove(file);
                ownersByFile.remove(file);
                openFiles.remove(file);
            }
            currentBytes = Math.max(0L, currentBytes - previous);
            currentBytes = addSaturated(currentBytes, actual);
        }

        private Path requireOwnedFile(Path file) throws IOException {
            Path normalized = file.toAbsolutePath().normalize();
            if (!directory.equals(normalized.getParent()) || Files.isSymbolicLink(normalized)) {
                throw new IOException("Artifact spool file is outside the configured private directory");
            }
            return normalized;
        }

        private void requireOwner(long ownerId) throws IOException {
            if (!owners.contains(ownerId)) {
                throw new IOException("Artifact spool is closed");
            }
        }

        private synchronized void closeLock() {
            closeQuietly(directoryLock);
            closeQuietly(lockChannel);
        }

        private static boolean isSpoolFile(Path file) {
            return file.getFileName().toString().endsWith(".tmp")
                    && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS);
        }

        private static long safeSize(Path file) {
            try {
                return Files.size(file);
            } catch (IOException exception) {
                return 0L;
            }
        }

        private static long addSaturated(long first, long second) {
            if (second > 0L && first > Long.MAX_VALUE - second) {
                return Long.MAX_VALUE;
            }
            return first + second;
        }

        private static void closeQuietly(AutoCloseable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception ignored) {
                // The original initialization or operation failure remains authoritative.
            }
        }
    }
}
