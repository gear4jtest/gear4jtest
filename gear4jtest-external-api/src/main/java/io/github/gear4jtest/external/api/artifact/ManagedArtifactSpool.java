package io.github.gear4jtest.external.api.artifact;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@io.github.gear4jtest.core.api.annotation.Internal
public final class ManagedArtifactSpool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedArtifactSpool.class);

    private final ArtifactSpoolPolicy policy;
    private final Path directory;
    private long currentFiles;
    private long currentBytes;
    private long staleFilesDeleted;
    private long staleBytesDeleted;
    private long quotaRejections;
    private long cleanupFailures;

    public ManagedArtifactSpool(ArtifactSpoolPolicy policy) throws IOException {
        this.policy = policy != null ? policy : ArtifactSpoolPolicy.defaults();
        this.directory = ArtifactSpoolFiles.prepareDirectory(this.policy.directory());
        initializeOccupancyAndCleanup();
    }

    public Path createTempFile(String prefix) throws IOException {
        Path file = ArtifactSpoolFiles.createTempFileInPreparedDirectory(directory, prefix);
        synchronized (this) {
            currentFiles++;
        }
        return file;
    }

    public OutputStream openOutput(Path file) throws IOException {
        requireOwnedFile(file);
        return new QuotaOutputStream(Files.newOutputStream(file), this);
    }

    public void copy(InputStream source, Path target) throws IOException {
        byte[] buffer = new byte[8192];
        try (OutputStream output = openOutput(target)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }

    public void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            requireOwnedFile(file);
            long size = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? Files.size(file) : 0L;
            if (Files.deleteIfExists(file)) {
                synchronized (this) {
                    currentFiles = Math.max(0L, currentFiles - 1L);
                    currentBytes = Math.max(0L, currentBytes - size);
                }
            }
        } catch (IOException | RuntimeException exception) {
            synchronized (this) {
                cleanupFailures++;
            }
            LOGGER.warn("Unable to delete an artifact spool file.", exception);
        }
    }

    public synchronized ArtifactSpoolStats snapshotStats() {
        return new ArtifactSpoolStats(currentFiles, currentBytes, policy.maxBytes(), staleFilesDeleted,
                staleBytesDeleted, quotaRejections, cleanupFailures);
    }

    private void initializeOccupancyAndCleanup() throws IOException {
        Instant cutoff = Instant.now().minus(policy.staleFileAge());
        List<Path> files;
        try (var entries = Files.list(directory)) {
            files = entries.filter(ManagedArtifactSpool::isSpoolFile).toList();
        }
        for (Path file : files) {
            ArtifactSpoolFiles.secureFilePermissions(file);
            long size = safeSize(file);
            try {
                FileTime lastModified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
                if (lastModified.toInstant().isBefore(cutoff) && Files.deleteIfExists(file)) {
                    staleFilesDeleted++;
                    staleBytesDeleted += size;
                    continue;
                }
            } catch (IOException exception) {
                cleanupFailures++;
                LOGGER.warn("Unable to inspect or delete a stale artifact spool file.", exception);
            }
            currentFiles++;
            currentBytes = addSaturated(currentBytes, size);
        }
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

    private void requireOwnedFile(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        if (!directory.equals(normalized.getParent()) || Files.isSymbolicLink(normalized)) {
            throw new IOException("Artifact spool file is outside the configured private directory");
        }
    }

    private synchronized void reserve(long bytes) throws IOException {
        long nextSize = addSaturated(currentBytes, bytes);
        if (policy.maxBytes() >= 0 && nextSize > policy.maxBytes()) {
            quotaRejections++;
            throw new IOException("Artifact spool quota exceeded. maxBytes=" + policy.maxBytes()
                    + ", currentBytes=" + currentBytes + ", requestedBytes=" + bytes);
        }
        currentBytes = nextSize;
    }

    private synchronized void release(long bytes) {
        currentBytes = Math.max(0L, currentBytes - bytes);
    }

    private static long addSaturated(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private static final class QuotaOutputStream extends FilterOutputStream {
        private final ManagedArtifactSpool spool;

        QuotaOutputStream(OutputStream output, ManagedArtifactSpool spool) {
            super(output);
            this.spool = spool;
        }

        @Override
        public void write(int value) throws IOException {
            spool.reserve(1L);
            try {
                out.write(value);
            } catch (IOException | RuntimeException exception) {
                spool.release(1L);
                throw exception;
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return;
            }
            spool.reserve(length);
            try {
                out.write(buffer, offset, length);
            } catch (IOException | RuntimeException exception) {
                spool.release(length);
                throw exception;
            }
        }
    }
}
