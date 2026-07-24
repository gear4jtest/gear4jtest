package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FilesystemArtifactStore implements ArtifactStore, ArtifactStoreMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilesystemArtifactStore.class);
    private static final long MAX_JAVA_BYTE_ARRAY_SIZE = Integer.MAX_VALUE - 8L;

    private final Path root;
    private final long maxArtifactSizeBytes;
    private final TempFileCleaner tempFileCleaner;
    private final FilesystemArtifactStoreMetrics metrics = new FilesystemArtifactStoreMetrics();

    /**
     * Creates a filesystem store with the default 5 MiB write and in-memory read
     * limit.
     *
     * @param root private application-owned storage root
     * @throws IOException if the root cannot be prepared securely
     */
    public FilesystemArtifactStore(Path root) throws IOException {
        this(root, DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
    }

    /**
     * Creates a filesystem store with a finite write and in-memory read limit.
     *
     * @param root                 private application-owned storage root
     * @param maxArtifactSizeBytes maximum accepted artifact size in bytes
     * @throws IOException              if the root cannot be prepared securely
     * @throws IllegalArgumentException if the limit is negative, unbounded or
     *                                  cannot be represented by an in-memory
     *                                  snapshot
     */
    public FilesystemArtifactStore(Path root, long maxArtifactSizeBytes) throws IOException {
        this(root, maxArtifactSizeBytes, Files::deleteIfExists);
    }

    FilesystemArtifactStore(Path root, TempFileCleaner tempFileCleaner) throws IOException {
        this(root, DEFAULT_MAX_ARTIFACT_SIZE_BYTES, tempFileCleaner);
    }

    FilesystemArtifactStore(Path root,
                            long maxArtifactSizeBytes,
                            TempFileCleaner tempFileCleaner)
            throws IOException {
        Path requiredRoot = Objects.requireNonNull(root, "root must not be null");
        long validatedMaxArtifactSizeBytes = validateConfiguredMaxArtifactSize(maxArtifactSizeBytes);
        this.root = SecureArtifactFiles.prepareRoot(requiredRoot);
        this.maxArtifactSizeBytes = validatedMaxArtifactSizeBytes;
        this.tempFileCleaner = Objects.requireNonNull(tempFileCleaner, "tempFileCleaner must not be null");
    }

    private Path pathForValidatedHash(String hash) {
        String firstSegment = hash.substring(0, 2);
        String secondSegment = hash.substring(2, 4);
        return root.resolve(firstSegment).resolve(secondSegment).resolve(hash);
    }

    @Override
    public String put(byte[] content) throws IOException {
        Objects.requireNonNull(content, "content must not be null");
        try {
            requireAllowedSize(content.length, maxArtifactSizeBytes);
        } catch (IOException exception) {
            metrics.recordWriteFailure(0L);
            throw exception;
        }
        return put(new ByteArrayInputStream(content), content.length);
    }

    @Override
    public String put(InputStream in, long maxBytes) throws IOException {
        Objects.requireNonNull(in, "input stream must not be null");
        long effectiveMaxBytes = minimumLimit(maxArtifactSizeBytes, validateRequestedMaxBytes(maxBytes));
        long startedNanos = System.nanoTime();
        Path tmp = null;
        try {
            tmp = SecureArtifactFiles.createPrivateTempFile(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            byte[] buffer = new byte[8192];
            try (var out = Files.newOutputStream(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                                                 LinkOption.NOFOLLOW_LINKS)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (total > Long.MAX_VALUE - read || total + read > effectiveMaxBytes) {
                        throw new IOException("Artifact size exceeds configured limit. maxBytes=" + effectiveMaxBytes);
                    }
                    total += read;
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String hash = HexFormat.of().formatHex(digest.digest());
            publish(tmp, hash);
            metrics.recordWriteCompleted(total, FilesystemArtifactStoreMetrics.elapsedSince(startedNanos));
            return hash;
        } catch (IOException e) {
            metrics.recordWriteFailure(FilesystemArtifactStoreMetrics.elapsedSince(startedNanos));
            throw e;
        } catch (Exception e) {
            metrics.recordWriteFailure(FilesystemArtifactStoreMetrics.elapsedSince(startedNanos));
            throw new IOException("Unable to write filesystem artifact", e);
        } finally {
            cleanupTempFile(tmp);
        }
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
        Path path = pathForValidatedHash(hash);
        long startedNanos = System.nanoTime();
        try {
            if (!SecureArtifactFiles.requireExistingArtifactParent(root, path.getParent())) {
                return Optional.empty();
            }
            BasicFileAttributes attributes = SecureArtifactFiles.readRegularFileAttributesIfPresent(path);
            if (attributes == null) {
                return Optional.empty();
            }
            metrics.recordReadOpened();
            byte[] content = readVerifiedContent(path, hash, attributes);
            metrics.recordReadCompleted(content.length, FilesystemArtifactStoreMetrics.elapsedSince(startedNanos));
            return Optional.of(Artifact.streaming(hash, content.length, Map.of(),
                                                  () -> new ByteArrayInputStream(content)));
        } catch (IOException | RuntimeException exception) {
            metrics.recordReadFailure(FilesystemArtifactStoreMetrics.elapsedSince(startedNanos));
            throw exception;
        }
    }

    @Override
    public boolean exists(String hashHex) throws IOException {
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
        Path path = pathForValidatedHash(hash);
        if (!SecureArtifactFiles.requireExistingArtifactParent(root, path.getParent())) {
            return false;
        }
        BasicFileAttributes attributes = SecureArtifactFiles.readRegularFileAttributesIfPresent(path);
        if (attributes == null) {
            return false;
        }
        requireReadableSize(hash, attributes.size());
        verifyContentHash(path, hash);
        return true;
    }

    @Override
    public ArtifactStoreStats snapshotStats() {
        return metrics.snapshot();
    }

    private void publish(Path tmp, String hash) throws IOException {
        Path target = pathForValidatedHash(hash);
        SecureArtifactFiles.prepareArtifactParent(root, hash.substring(0, 2), hash.substring(2, 4));
        try {
            Files.move(tmp, target);
            SecureArtifactFiles.secureFilePermissions(target);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            BasicFileAttributes attributes = SecureArtifactFiles.readRegularFileAttributesIfPresent(target);
            if (attributes == null) {
                throw new IOException("Filesystem artifact disappeared during concurrent publication: " + hash,
                        exception);
            }
            readVerifiedContent(target, hash, attributes);
        }
    }

    private byte[] readVerifiedContent(Path path,
                                       String expectedHash,
                                       BasicFileAttributes attributes)
            throws IOException {
        requireReadableSize(expectedHash, attributes.size());
        MessageDigest digest = newSha256Digest();
        byte[] content = new byte[(int) attributes.size()];
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (var channel = Files.newByteChannel(path, options);
                InputStream input = Channels.newInputStream(channel)) {
            int total = 0;
            while (total < content.length) {
                int read = input.read(content, total, content.length - total);
                if (read == -1) {
                    throw new IOException("Filesystem artifact size changed during verification. hash=" + expectedHash
                            + ", expectedSizeBytes=" + content.length + ", actualSizeBytes=" + total);
                }
                if (read > 0) {
                    digest.update(content, total, read);
                    total += read;
                }
            }
            if (input.read() != -1) {
                throw new IOException("Filesystem artifact grew beyond the configured read limit during verification. "
                        + "hash=" + expectedHash + ", maxArtifactSizeBytes=" + maxArtifactSizeBytes);
            }
        }
        String actualHash = HexFormat.of().formatHex(digest.digest());
        if (!expectedHash.equals(actualHash)) {
            throw new IOException("Filesystem artifact integrity check failed for hash " + expectedHash);
        }
        return content;
    }

    private void requireReadableSize(String hash, long size) throws IOException {
        if (size < 0L || size > maxArtifactSizeBytes) {
            throw new IOException("Filesystem artifact exceeds configured read limit. hash=" + hash
                    + ", maxArtifactSizeBytes=" + maxArtifactSizeBytes + ", actualSizeBytes=" + size);
        }
    }

    private static long validateConfiguredMaxArtifactSize(long maxBytes) {
        if (maxBytes < 0L) {
            throw new IllegalArgumentException("maxArtifactSizeBytes must be >= 0; UNLIMITED_SIZE is unsupported "
                    + "because filesystem reads are materialized in memory");
        }
        if (maxBytes > MAX_JAVA_BYTE_ARRAY_SIZE) {
            throw new IllegalArgumentException("maxArtifactSizeBytes exceeds the largest supported in-memory snapshot: "
                    + MAX_JAVA_BYTE_ARRAY_SIZE);
        }
        return maxBytes;
    }

    private static long validateRequestedMaxBytes(long maxBytes) {
        if (maxBytes < UNLIMITED_SIZE) {
            throw new IllegalArgumentException("maxBytes must be -1 or >= 0");
        }
        return maxBytes;
    }

    private static long minimumLimit(long configuredLimit, long requestedLimit) {
        if (requestedLimit == UNLIMITED_SIZE) {
            return configuredLimit;
        }
        return Math.min(configuredLimit, requestedLimit);
    }

    private static void requireAllowedSize(long size, long maxBytes) throws IOException {
        if (size > maxBytes) {
            throw new IOException("Artifact size exceeds configured limit. maxBytes=" + maxBytes
                    + ", actualSizeBytes=" + size);
        }
    }

    private static void verifyContentHash(Path path, String expectedHash) throws IOException {
        MessageDigest digest = newSha256Digest();
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (var channel = Files.newByteChannel(path, options);
                InputStream input = Channels.newInputStream(channel)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        String actualHash = HexFormat.of().formatHex(digest.digest());
        if (!expectedHash.equals(actualHash)) {
            throw new IOException("Filesystem artifact integrity check failed for hash " + expectedHash);
        }
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void cleanupTempFile(Path tmp) {
        if (tmp == null) {
            return;
        }
        try {
            tempFileCleaner.deleteIfExists(tmp);
        } catch (IOException | RuntimeException exception) {
            long failures = metrics.recordCleanupFailure();
            if (failures == 1L || Long.bitCount(failures) == 1) {
                LOGGER.warn("Unable to delete a filesystem artifact temp file. cleanupFailures={}", failures,
                            exception);
            }
        }
    }

    @FunctionalInterface
    interface TempFileCleaner {
        boolean deleteIfExists(Path path) throws IOException;
    }
}
