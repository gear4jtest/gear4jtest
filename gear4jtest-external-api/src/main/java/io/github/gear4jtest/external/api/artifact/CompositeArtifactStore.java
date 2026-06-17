package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompositeArtifactStore implements ArtifactStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeArtifactStore.class);

    private final ArtifactStore primary;
    private final java.util.List<ArtifactStore> fallbacks;
    private final WriteMode writeMode;
    private final ReadMode readMode;
    private final boolean verifyOnRead;
    private final boolean selfHealing;
    private final long verificationMaxArtifactSizeBytes;
    private final Executor asyncExec;

    public CompositeArtifactStore(ArtifactStore primary,
                                  java.util.List<ArtifactStore> fallbacks,
                                  WriteMode writeMode,
                                  ReadMode readMode,
                                  boolean verifyOnRead,
                                  boolean selfHealing,
                                  Executor asyncExec) {
        this(primary, fallbacks, writeMode, readMode, verifyOnRead, selfHealing,
                ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES, asyncExec);
    }

    public CompositeArtifactStore(ArtifactStore primary,
                                  java.util.List<ArtifactStore> fallbacks,
                                  WriteMode writeMode,
                                  ReadMode readMode,
                                  boolean verifyOnRead,
                                  boolean selfHealing,
                                  long verificationMaxArtifactSizeBytes,
                                  Executor asyncExec) {
        this.primary = Objects.requireNonNull(primary);
        this.fallbacks = java.util.List.copyOf(Objects.requireNonNull(fallbacks));
        this.writeMode = writeMode == null ? WriteMode.PRIMARY_ONLY : writeMode;
        this.readMode = readMode == null ? ReadMode.PREFER_PRIMARY : readMode;
        this.verifyOnRead = verifyOnRead;
        this.selfHealing = selfHealing;
        this.verificationMaxArtifactSizeBytes = validateVerificationMaxArtifactSizeBytes(
                verificationMaxArtifactSizeBytes);
        this.asyncExec = asyncExec != null ? asyncExec : ArtifactStoreExecutors.defaultAsyncExecutor();
    }

    @Override
    public String put(byte[] content) throws IOException {
        byte[] stored = Arrays.copyOf(Objects.requireNonNull(content, "content must not be null"), content.length);
        String hash = primary.put(stored);
        switch (writeMode) {
            case PRIMARY_ONLY -> {
            }
            case SYNC_ALL -> {
                for (var fb : fallbacks) {
                    fb.put(stored);
                }
            }
            case ASYNC_FALLBACKS -> {
                for (var fb : fallbacks) {
                    byte[] fallbackContent = Arrays.copyOf(stored, stored.length);
                    asyncExec.execute(() -> {
                        try {
                            fb.put(fallbackContent);
                        } catch (IOException e) {
                            LOGGER.warn("Asynchronous fallback artifact write failed.", e);
                        }
                    });
                }
            }
        }
        return hash;
    }

    @Override
    public String put(InputStream in, long maxBytes) throws IOException {
        Objects.requireNonNull(in, "input stream must not be null");
        TempArtifact temp = spoolToTempFile(in, maxBytes);
        try {
            return putFromTempFile(temp.path());
        } finally {
            deleteTempFile(temp.path());
        }
    }

    private String putFromTempFile(Path tempFile) throws IOException {
        String hash;
        try (InputStream primaryInput = Files.newInputStream(tempFile)) {
            hash = primary.put(primaryInput, ArtifactStore.UNLIMITED_SIZE);
        }
        switch (writeMode) {
            case PRIMARY_ONLY -> {
            }
            case SYNC_ALL -> {
                for (var fb : fallbacks) {
                    try (InputStream fallbackInput = Files.newInputStream(tempFile)) {
                        fb.put(fallbackInput, ArtifactStore.UNLIMITED_SIZE);
                    }
                }
            }
            case ASYNC_FALLBACKS -> {
                for (var fb : fallbacks) {
                    scheduleAsyncWrite(fb, tempFile, "Asynchronous fallback artifact write failed.");
                }
            }
        }
        return hash;
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        String hash = Hashing.requireSha256Hex(hashHex);
        var artifact = primary.get(hash);
        if (artifact.isPresent()) {
            return maybeVerifyAndHeal(hash, artifact.get(), true);
        }
        for (var fallback : fallbacks) {
            var fallbackArtifact = fallback.get(hash);
            if (fallbackArtifact.isPresent()) {
                return maybeVerifyAndHeal(hash, fallbackArtifact.get(), false);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean exists(String hashHex) throws IOException {
        String hash = Hashing.requireSha256Hex(hashHex);
        if (primary.exists(hash)) {
            return true;
        }
        for (var fallback : fallbacks) {
            if (fallback.exists(hash)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Artifact> maybeVerifyAndHeal(String hash, Artifact artifact, boolean fromPrimary)
            throws IOException {
        if (!verifyOnRead) {
            return Optional.of(artifact);
        }
        try (var in = artifact.openStream()) {
            if (!fromPrimary && selfHealing) {
                TempArtifact temp = spoolToTempFile(in, verificationMaxArtifactSizeBytes);
                try {
                    if (!temp.hashHex().equals(hash)) {
                        throw new IOException("Corrupt artifact: " + hash);
                    }
                    scheduleAsyncPrimaryHealing(hash, temp.path());
                } finally {
                    deleteTempFile(temp.path());
                }
            } else {
                String rehash = Hashing.sha256Hex(in, verificationMaxArtifactSizeBytes).hashHex();
                if (!rehash.equals(hash)) {
                    throw new IOException("Corrupt artifact: " + hash);
                }
            }
        }
        return Optional.of(artifact);
    }

    private void scheduleAsyncPrimaryHealing(String hash, Path sourceFile) {
        Path asyncCopy;
        try {
            asyncCopy = copyTempFile(sourceFile);
        } catch (IOException e) {
            LOGGER.warn("Unable to prepare artifact self-healing copy.", e);
            return;
        }
        try {
            asyncExec.execute(() -> {
                try (InputStream content = Files.newInputStream(asyncCopy)) {
                    if (!primary.exists(hash)) {
                        primary.put(content, ArtifactStore.UNLIMITED_SIZE);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Asynchronous artifact self-healing write failed.", e);
                } finally {
                    deleteTempFile(asyncCopy);
                }
            });
        } catch (RuntimeException e) {
            deleteTempFile(asyncCopy);
            LOGGER.warn("Asynchronous artifact self-healing write was rejected.", e);
        }
    }

    private void scheduleAsyncWrite(ArtifactStore store, Path sourceFile, String failureMessage) {
        Path asyncCopy;
        try {
            asyncCopy = copyTempFile(sourceFile);
        } catch (IOException e) {
            LOGGER.warn("Unable to prepare asynchronous artifact copy.", e);
            return;
        }
        try {
            asyncExec.execute(() -> {
                try (InputStream content = Files.newInputStream(asyncCopy)) {
                    store.put(content, ArtifactStore.UNLIMITED_SIZE);
                } catch (IOException e) {
                    LOGGER.warn(failureMessage, e);
                } finally {
                    deleteTempFile(asyncCopy);
                }
            });
        } catch (RuntimeException e) {
            deleteTempFile(asyncCopy);
            LOGGER.warn("Asynchronous artifact write was rejected.", e);
        }
    }

    private TempArtifact spoolToTempFile(InputStream in, long maxBytes) throws IOException {
        Path tmp = Files.createTempFile("gear4j-artifact-composite-", ".tmp");
        try {
            copyWithLimit(in, tmp, maxBytes);
            try (InputStream digestInput = Files.newInputStream(tmp)) {
                String hash = Hashing.sha256Hex(digestInput, ArtifactStore.UNLIMITED_SIZE).hashHex();
                return new TempArtifact(tmp, hash);
            }
        } catch (IOException | RuntimeException e) {
            deleteTempFile(tmp);
            throw e;
        }
    }

    private static long validateVerificationMaxArtifactSizeBytes(long maxBytes) {
        if (maxBytes == 0 || maxBytes < ArtifactStore.UNLIMITED_SIZE) {
            throw new IllegalArgumentException("verificationMaxArtifactSizeBytes must be > 0 or UNLIMITED_SIZE");
        }
        return maxBytes;
    }

    private static void copyWithLimit(InputStream in, Path target, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (var out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (maxBytes >= 0 && total > maxBytes) {
                    throw new IOException("Artifact size exceeds configured limit. maxBytes=" + maxBytes);
                }
                out.write(buffer, 0, read);
            }
        }
    }

    private static Path copyTempFile(Path sourceFile) throws IOException {
        Path copy = Files.createTempFile("gear4j-artifact-async-", ".tmp");
        try {
            Files.copy(sourceFile, copy, StandardCopyOption.REPLACE_EXISTING);
            return copy;
        } catch (IOException | RuntimeException e) {
            deleteTempFile(copy);
            throw e;
        }
    }

    private static void deleteTempFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOGGER.warn("Unable to delete temporary artifact file {}.", file, e);
        }
    }

    private record TempArtifact(Path path, String hashHex) {}

    public enum WriteMode {
        PRIMARY_ONLY, SYNC_ALL, ASYNC_FALLBACKS
    }

    public enum ReadMode {
        /**
         * Read from the primary store first, then fall back to configured fallback
         * stores.
         */
        PREFER_PRIMARY
    }
}
