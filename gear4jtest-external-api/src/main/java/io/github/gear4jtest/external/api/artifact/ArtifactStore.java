package io.github.gear4jtest.external.api.artifact;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import io.github.gear4jtest.core.api.annotation.Spi;

/**
 * Content-addressed artifact store. Implementations should stream writes when
 * the backing store supports it and must enforce {@code maxBytes} before
 * accepting the artifact.
 */
@Spi
public interface ArtifactStore {
    long UNLIMITED_SIZE = -1L;
    long DEFAULT_MAX_ARTIFACT_SIZE_BYTES = 5L * 1024L * 1024L;

    String put(byte[] content) throws IOException;

    default String put(InputStream in) throws IOException {
        return put(in, DEFAULT_MAX_ARTIFACT_SIZE_BYTES);
    }

    default String put(InputStream in, long maxBytes) throws IOException {
        return put(readAllBytes(in, maxBytes));
    }

    Optional<Artifact> get(String hashHex) throws IOException;

    boolean exists(String hashHex) throws IOException;

    static byte[] readAllBytes(InputStream in, long maxBytes) throws IOException {
        if (in == null) {
            throw new NullPointerException("input stream must not be null");
        }
        if (maxBytes < 0) {
            return in.readAllBytes();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Artifact size exceeds configured limit. maxBytes=" + maxBytes);
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
