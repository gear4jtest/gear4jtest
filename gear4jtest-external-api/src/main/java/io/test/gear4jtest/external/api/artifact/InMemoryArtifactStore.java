package io.test.gear4jtest.external.api.artifact;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryArtifactStore implements ArtifactStore {
    private final Map<String, byte[]> map = new ConcurrentHashMap<>();

    @Override
    public String put(byte[] content) {
        String hash = Hashing.sha256Hex(content);
        map.putIfAbsent(hash, content);
        return hash;
    }

    @Override
    public Optional<Artifact> get(String hashHex) {
        byte[] data = map.get(hashHex);
        if (data == null)
            return Optional.empty();
        return Optional.of(new Artifact(hashHex, data.length, Map.of(), () -> new ByteArrayInputStream(data)));
    }

    @Override
    public boolean exists(String hashHex) {
        return map.containsKey(hashHex);
    }
}
