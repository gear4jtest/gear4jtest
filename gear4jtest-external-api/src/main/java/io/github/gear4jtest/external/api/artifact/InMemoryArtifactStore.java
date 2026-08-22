package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class InMemoryArtifactStore implements ArtifactStore {
    public static final long DEFAULT_MAX_TOTAL_BYTES = 64L * 1024L * 1024L;
    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final Map<String, byte[]> map = new HashMap<>();
    private final long maxArtifactSizeBytes;
    private final long maxTotalBytes;
    private final int maxEntries;
    private long totalBytes;
    private boolean closed;

    public InMemoryArtifactStore() {
        this(DEFAULT_MAX_ARTIFACT_SIZE_BYTES, DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_ENTRIES);
    }

    public InMemoryArtifactStore(long maxArtifactSizeBytes, long maxTotalBytes, int maxEntries) {
        this.maxArtifactSizeBytes = requireLimit(maxArtifactSizeBytes, "maxArtifactSizeBytes");
        this.maxTotalBytes = requirePositiveLimit(maxTotalBytes, "maxTotalBytes");
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        this.maxEntries = maxEntries;
    }

    @Override
    public synchronized String put(byte[] content) throws IOException {
        requireOpen();
        byte[] stored = Arrays.copyOf(Objects.requireNonNull(content, "content must not be null"), content.length);
        requireWithinLimit(stored.length, maxArtifactSizeBytes, "artifact");
        String hash = ArtifactHashes.sha256Hex(stored);
        if (map.containsKey(hash)) {
            return hash;
        }
        if (map.size() >= maxEntries) {
            throw new IOException("In-memory artifact entry limit exceeded. maxEntries=" + maxEntries);
        }
        long nextTotal = addExact(totalBytes, stored.length);
        requireWithinLimit(nextTotal, maxTotalBytes, "in-memory store");
        map.put(hash, stored);
        totalBytes = nextTotal;
        return hash;
    }

    @Override
    public String put(InputStream in, long maxBytes) throws IOException {
        long effectiveLimit = minimumLimit(maxArtifactSizeBytes,
                                           requireLimit(maxBytes, "maxBytes"));
        return put(ArtifactStore.readAllBytes(in, effectiveLimit));
    }

    @Override
    public synchronized Optional<Artifact> get(String hashHex) {
        requireOpen();
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
        byte[] data = map.get(hash);
        if (data == null) {
            return Optional.empty();
        }
        byte[] snapshot = Arrays.copyOf(data, data.length);
        return Optional.of(new Artifact(hash, snapshot.length, Map.of(), () -> new ByteArrayInputStream(snapshot)));
    }

    @Override
    public synchronized boolean exists(String hashHex) {
        requireOpen();
        String hash = ArtifactHashes.requireSha256Hex(hashHex);
        return map.containsKey(hash);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        map.clear();
        totalBytes = 0L;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("In-memory artifact store is closed");
        }
    }

    private static long requirePositiveLimit(long limit, String name) {
        if (limit == 0L || limit < UNLIMITED_SIZE) {
            throw new IllegalArgumentException(name + " must be > 0 or UNLIMITED_SIZE");
        }
        return limit;
    }

    private static long requireLimit(long limit, String name) {
        if (limit < UNLIMITED_SIZE) {
            throw new IllegalArgumentException(name + " must be >= 0 or UNLIMITED_SIZE");
        }
        return limit;
    }

    private static void requireWithinLimit(long actual, long limit, String subject) throws IOException {
        if (limit >= 0L && actual > limit) {
            throw new IOException(subject + " byte limit exceeded. maxBytes=" + limit + ", actualBytes=" + actual);
        }
    }

    private static long minimumLimit(long first, long second) {
        if (first == UNLIMITED_SIZE) {
            return second;
        }
        if (second == UNLIMITED_SIZE) {
            return first;
        }
        return Math.min(first, second);
    }

    private static long addExact(long first, long second) throws IOException {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            throw new IOException("In-memory artifact byte accounting overflow", exception);
        }
    }
}
