package io.test.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryArtifactStore implements ArtifactStore {
    private final Map<String, byte[]> map = new ConcurrentHashMap<>();

    @Override
    public String put(byte[] content) {
        byte[] stored = Arrays.copyOf(Objects.requireNonNull(content, "content must not be null"), content.length);
        String hash = Hashing.sha256Hex(stored);
        map.putIfAbsent(hash, stored);
        return hash;
    }

    @Override
    public Optional<Artifact> get(String hashHex) {
        String hash = Hashing.requireSha256Hex(hashHex);
        byte[] data = map.get(hash);
        if (data == null) {
            return Optional.empty();
        }
        byte[] snapshot = Arrays.copyOf(data, data.length);
        return Optional.of(new Artifact(hash, snapshot.length, Map.of(), () -> new ByteArrayInputStream(snapshot)));
    }

    @Override
    public boolean exists(String hashHex) {
        String hash = Hashing.requireSha256Hex(hashHex);
        return map.containsKey(hash);
    }
}
