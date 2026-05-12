package io.test.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class CompositeArtifactStore implements ArtifactStore {

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
        this.asyncExec = asyncExec != null ? asyncExec : Executors.newCachedThreadPool();
    }

    @Override
    public String put(byte[] content) throws IOException {
        String hash = primary.put(content);
        switch (writeMode) {
            case PRIMARY_ONLY -> {
            }
            case SYNC_ALL -> {
                for (var fb : fallbacks)
                    fb.put(content);
            }
            case ASYNC_FALLBACKS -> {
                for (var fb : fallbacks)
                    asyncExec.execute(() -> {
                        try {
                            fb.put(content);
                        } catch (IOException ignored) {
                        }
                    });
            }
        }
        return hash;
    }

    @Override
    public Optional<Artifact> get(String hashHex) throws IOException {
        if (readMode == ReadMode.PREFER_PRIMARY) {
            var a = primary.get(hashHex);
            if (a.isPresent())
                return maybeVerifyAndHeal(hashHex, a.get(), true);
            for (var fb : fallbacks) {
                var b = fb.get(hashHex);
                if (b.isPresent())
                    return maybeVerifyAndHeal(hashHex, b.get(), false);
            }
            return Optional.empty();
        } else {
            var a = primary.get(hashHex);
            if (a.isPresent())
                return maybeVerifyAndHeal(hashHex, a.get(), true);
            for (var fb : fallbacks) {
                var b = fb.get(hashHex);
                if (b.isPresent())
                    return maybeVerifyAndHeal(hashHex, b.get(), false);
            }
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String hashHex) throws IOException {
        if (primary.exists(hashHex))
            return true;
        for (var fb : fallbacks)
            if (fb.exists(hashHex))
                return true;
        return false;
    }

    private Optional<Artifact> maybeVerifyAndHeal(String hash, Artifact a, boolean fromPrimary) throws IOException {
        if (!verifyOnRead)
            return Optional.of(a);
        try (var in = a.openStream()) {
            var data = in.readAllBytes();
            String rehash = Hashing.sha256Hex(data);
            if (!rehash.equalsIgnoreCase(hash))
                throw new IOException("Corrupt artifact: " + hash);
            if (!fromPrimary && selfHealing) {
                asyncExec.execute(() -> {
                    try {
                        if (!primary.exists(hash))
                            primary.put(data);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        return Optional.of(a);
    }

    public enum WriteMode {
        PRIMARY_ONLY, SYNC_ALL, ASYNC_FALLBACKS
    }

    public enum ReadMode {
        PREFER_PRIMARY, FIRST_AVAILABLE
    }
}
