package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface ArtifactStore {
    String put(byte[] content) throws IOException;

    default String put(InputStream in) throws IOException {
        return put(in.readAllBytes());
    }

    Optional<Artifact> get(String hashHex) throws IOException;

    boolean exists(String hashHex) throws IOException;
}
