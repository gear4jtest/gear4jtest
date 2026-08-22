package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CompositeArtifactStore implements ArtifactStore, ArtifactSpoolMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompositeArtifactStore.class);

    private final ArtifactStore primary;
    private final java.util.List<ArtifactStore> fallbacks;
    private final WriteMode writeMode;
    private final ReadMode readMode;
    private final boolean verifyOnRead;
    private final boolean selfHealing;
    private final long verificationMaxArtifactSizeBytes;
    private final ManagedArtifactSpool spool;
    private final Executor asyncExec;
    private final Object lifecycleMonitor = new Object();
    private int pendingAsyncTasks;
    private boolean closing;
    private boolean resourcesClosed;

    public CompositeArtifactStore(ArtifactStore primary,
                                  java.util.List<ArtifactStore> fallbacks,
                                  WriteMode writeMode,
                                  ReadMode readMode,
                                  boolean verifyOnRead,
                                  boolean selfHealing,
                                  Executor asyncExec) {
        this(primary, fallbacks, writeMode, readMode, verifyOnRead, selfHealing,
                ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES, ArtifactSpoolPolicy.defaults(), asyncExec);
    }

    public CompositeArtifactStore(ArtifactStore primary,
                                  java.util.List<ArtifactStore> fallbacks,
                                  WriteMode writeMode,
                                  ReadMode readMode,
                                  boolean verifyOnRead,
                                  boolean selfHealing,
                                  long verificationMaxArtifactSizeBytes,
                                  Executor asyncExec) {
        this(primary, fallbacks, writeMode, readMode, verifyOnRead, selfHealing, verificationMaxArtifactSizeBytes,
                ArtifactSpoolPolicy.defaults(), asyncExec);
    }

    public CompositeArtifactStore(ArtifactStore primary,
                                  java.util.List<ArtifactStore> fallbacks,
                                  WriteMode writeMode,
                                  ReadMode readMode,
                                  boolean verifyOnRead,
                                  boolean selfHealing,
                                  long verificationMaxArtifactSizeBytes,
                                  Path spoolDirectory,
                                  Executor asyncExec) {
        this(primary, fallbacks, writeMode, readMode, verifyOnRead, selfHealing, verificationMaxArtifactSizeBytes,
                ArtifactSpoolPolicy.builder().directory(spoolDirectory).build(), asyncExec);
    }

    public CompositeArtifactStore(ArtifactStore primary,
                                  java.util.List<ArtifactStore> fallbacks,
                                  WriteMode writeMode,
                                  ReadMode readMode,
                                  boolean verifyOnRead,
                                  boolean selfHealing,
                                  long verificationMaxArtifactSizeBytes,
                                  ArtifactSpoolPolicy spoolPolicy,
                                  Executor asyncExec) {
        this.primary = Objects.requireNonNull(primary);
        this.fallbacks = java.util.List.copyOf(Objects.requireNonNull(fallbacks));
        this.writeMode = writeMode == null ? WriteMode.PRIMARY_ONLY : writeMode;
        this.readMode = readMode == null ? ReadMode.PREFER_PRIMARY : readMode;
        this.verifyOnRead = verifyOnRead;
        this.selfHealing = selfHealing;
        this.verificationMaxArtifactSizeBytes = validateVerificationMaxArtifactSizeBytes(
                                                                                         verificationMaxArtifactSizeBytes);
        try {
            this.spool = new ManagedArtifactSpool(spoolPolicy);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to initialize the private artifact spool", exception);
        }
        this.asyncExec = asyncExec != null ? asyncExec : ArtifactStoreExecutors.defaultAsyncExecutor();
    }

    @Override
    public ArtifactSpoolStats snapshotSpoolStats() {
        return spool.snapshotStats();
    }

    @Override
    public String put(byte[] content) throws IOException {
        requireOpen();
        byte[] stored = Arrays.copyOf(Objects.requireNonNull(content, "content must not be null"), content.length);
        String hash = primary.put(stored);
        switch (writeMode) {
            case PRIMARY_ONLY -> {
                // Primary write already completed; no fallback write is required.
            }
            case SYNC_ALL -> {
                for (var fb : fallbacks) {
                    fb.put(stored);
                }
            }
            case ASYNC_FALLBACKS -> {
                scheduleAsyncWrites(fallbacks, stored);
            }
        }
        return hash;
    }

    @Override
    public String put(InputStream in, long maxBytes) throws IOException {
        requireOpen();
        Objects.requireNonNull(in, "input stream must not be null");
        TempArtifact temp = spoolToTempFile(in, maxBytes);
        try {
            return putFromTempFile(temp.path());
        } finally {
            spool.delete(temp.path());
        }
    }

    private String putFromTempFile(Path tempFile) throws IOException {
        String hash;
        try (InputStream primaryInput = Files.newInputStream(tempFile)) {
            hash = primary.put(primaryInput, ArtifactStore.UNLIMITED_SIZE);
        }
        switch (writeMode) {
            case PRIMARY_ONLY -> {
                // Primary write already completed; no fallback write is required.
            }
            case SYNC_ALL -> {
                for (var fb : fallbacks) {
                    try (InputStream fallbackInput = Files.newInputStream(tempFile)) {
                        fb.put(fallbackInput, ArtifactStore.UNLIMITED_SIZE);
                    }
                }
            }
            case ASYNC_FALLBACKS -> {
                scheduleAsyncWrites(fallbacks, tempFile, "Asynchronous fallback artifact write failed.");
            }
        }
        return hash;
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        requireOpen();
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
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
        requireOpen();
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
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
        try (var in = artifact.openStreamChecked()) {
            if (!fromPrimary && selfHealing) {
                TempArtifact temp = spoolToTempFile(in, verificationMaxArtifactSizeBytes);
                try {
                    if (!temp.hashHex().equals(hash)) {
                        throw new ArtifactIntegrityException("Corrupt artifact: " + hash);
                    }
                    scheduleAsyncPrimaryHealing(hash, temp.path());
                } finally {
                    spool.delete(temp.path());
                }
            } else {
                String rehash = ArtifactHashes.sha256Hex(in, verificationMaxArtifactSizeBytes).hashHex();
                if (!rehash.equals(hash)) {
                    throw new ArtifactIntegrityException("Corrupt artifact: " + hash);
                }
            }
        }
        return Optional.of(artifact);
    }

    private void scheduleAsyncPrimaryHealing(String hash, Path sourceFile) {
        Path asyncCopy;
        try {
            asyncCopy = copyTempFile(sourceFile);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Unable to prepare artifact self-healing copy.", e);
            return;
        }
        executeBestEffort(() -> {
            try (InputStream content = Files.newInputStream(asyncCopy)) {
                if (!primary.exists(hash)) {
                    primary.put(content, ArtifactStore.UNLIMITED_SIZE);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Asynchronous artifact self-healing write failed.", e);
            } finally {
                spool.delete(asyncCopy);
            }
        }, () -> spool.delete(asyncCopy), "Asynchronous artifact self-healing write was rejected.");
    }

    private void scheduleAsyncWrites(java.util.List<ArtifactStore> stores, byte[] source) {
        if (stores.isEmpty()) {
            return;
        }
        Path asyncCopy;
        try {
            asyncCopy = copyToTempFile(new ByteArrayInputStream(source));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Unable to prepare asynchronous artifact copy after primary success.", e);
            return;
        }
        schedulePreparedAsyncWrites(stores, asyncCopy, "Asynchronous fallback artifact write failed.");
    }

    private void scheduleAsyncWrites(java.util.List<ArtifactStore> stores, Path sourceFile, String failureMessage) {
        if (stores.isEmpty()) {
            return;
        }
        Path asyncCopy;
        try {
            asyncCopy = copyTempFile(sourceFile);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Unable to prepare asynchronous artifact copy.", e);
            return;
        }
        schedulePreparedAsyncWrites(stores, asyncCopy, failureMessage);
    }

    private void schedulePreparedAsyncWrites(java.util.List<ArtifactStore> stores,
                                             Path asyncCopy,
                                             String failureMessage) {
        executeBestEffort(() -> {
            try {
                for (ArtifactStore store : stores) {
                    try (InputStream content = Files.newInputStream(asyncCopy)) {
                        store.put(content, ArtifactStore.UNLIMITED_SIZE);
                    } catch (IOException | RuntimeException e) {
                        LOGGER.warn("{} fallbackStore={}", failureMessage, store.getClass().getName(), e);
                    }
                }
            } finally {
                spool.delete(asyncCopy);
            }
        }, () -> spool.delete(asyncCopy),
                          "Asynchronous fallback artifact write was rejected after primary success.");
    }

    private void executeBestEffort(Runnable task, Runnable rejectionCleanup, String rejectionMessage) {
        if (!registerAsyncTask()) {
            rejectionCleanup.run();
            LOGGER.warn("{} Artifact store is closing.", rejectionMessage);
            return;
        }
        try {
            asyncExec.execute(() -> {
                try {
                    task.run();
                } finally {
                    completeAsyncTask();
                }
            });
        } catch (RuntimeException rejected) {
            completeAsyncTask();
            rejectionCleanup.run();
            LOGGER.warn(rejectionMessage, rejected);
        } catch (Error error) {
            completeAsyncTask();
            rejectionCleanup.run();
            throw error;
        }
    }

    @Override
    public void close() {
        boolean closeNow;
        synchronized (lifecycleMonitor) {
            if (closing) {
                return;
            }
            closing = true;
            closeNow = pendingAsyncTasks == 0;
        }
        if (closeNow) {
            closeResources();
        }
    }

    private void requireOpen() throws IOException {
        synchronized (lifecycleMonitor) {
            if (closing) {
                throw new IOException("Composite artifact store is closed");
            }
        }
    }

    private boolean registerAsyncTask() {
        synchronized (lifecycleMonitor) {
            if (closing) {
                return false;
            }
            pendingAsyncTasks = Math.addExact(pendingAsyncTasks, 1);
            return true;
        }
    }

    private void completeAsyncTask() {
        boolean closeNow;
        synchronized (lifecycleMonitor) {
            pendingAsyncTasks--;
            closeNow = closing && pendingAsyncTasks == 0;
        }
        if (closeNow) {
            closeResources();
        }
    }

    private void closeResources() {
        synchronized (lifecycleMonitor) {
            if (resourcesClosed) {
                return;
            }
            resourcesClosed = true;
        }
        spool.close();
        IdentityHashMap<ArtifactStore, Boolean> closedStores = new IdentityHashMap<>();
        closedStores.put(primary, Boolean.TRUE);
        primary.close();
        for (ArtifactStore fallback : fallbacks) {
            if (closedStores.put(fallback, Boolean.TRUE) == null) {
                fallback.close();
            }
        }
    }

    private TempArtifact spoolToTempFile(InputStream in, long maxBytes) throws IOException {
        Path tmp = spool.createTempFile("composite-");
        try {
            copyWithLimit(in, tmp, maxBytes);
            try (InputStream digestInput = Files.newInputStream(tmp)) {
                String hash = ArtifactHashes.sha256Hex(digestInput, ArtifactStore.UNLIMITED_SIZE).hashHex();
                return new TempArtifact(tmp, hash);
            }
        } catch (IOException | RuntimeException e) {
            spool.delete(tmp);
            throw e;
        }
    }

    private static long validateVerificationMaxArtifactSizeBytes(long maxBytes) {
        if (maxBytes == 0 || maxBytes < ArtifactStore.UNLIMITED_SIZE) {
            throw new IllegalArgumentException("verificationMaxArtifactSizeBytes must be > 0 or UNLIMITED_SIZE");
        }
        return maxBytes;
    }

    private void copyWithLimit(InputStream in, Path target, long maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        try (var out = spool.openOutput(target)) {
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

    private Path copyTempFile(Path sourceFile) throws IOException {
        try (InputStream source = Files.newInputStream(sourceFile)) {
            return copyToTempFile(source);
        }
    }

    private Path copyToTempFile(InputStream source) throws IOException {
        Path copy = spool.createTempFile("async-");
        try {
            spool.copy(source, copy);
            return copy;
        } catch (IOException | RuntimeException exception) {
            spool.delete(copy);
            throw exception;
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
