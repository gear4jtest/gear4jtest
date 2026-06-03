package io.test.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

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
    private final Executor asyncExec;

    public CompositeArtifactStore(ArtifactStore primary,
                                  java.util.List<ArtifactStore> fallbacks,
                                  WriteMode writeMode,
                                  ReadMode readMode,
                                  boolean verifyOnRead,
                                  boolean selfHealing,
                                  Executor asyncExec) {
        this.primary = Objects.requireNonNull(primary);
        this.fallbacks = java.util.List.copyOf(Objects.requireNonNull(fallbacks));
        this.writeMode = writeMode == null ? WriteMode.PRIMARY_ONLY : writeMode;
        this.readMode = readMode == null ? ReadMode.PREFER_PRIMARY : readMode;
        this.verifyOnRead = verifyOnRead;
        this.selfHealing = selfHealing;
        this.asyncExec = asyncExec != null ? asyncExec : ForkJoinPool.commonPool();
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
            byte[] data = in.readAllBytes();
            String rehash = Hashing.sha256Hex(data);
            if (!rehash.equals(hash)) {
                throw new IOException("Corrupt artifact: " + hash);
            }
            if (!fromPrimary && selfHealing) {
                byte[] healed = Arrays.copyOf(data, data.length);
                asyncExec.execute(() -> {
                    try {
                        if (!primary.exists(hash)) {
                            primary.put(healed);
                        }
                    } catch (IOException e) {
                        LOGGER.warn("Asynchronous artifact self-healing write failed.", e);
                    }
                });
            }
        }
        return Optional.of(artifact);
    }

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
