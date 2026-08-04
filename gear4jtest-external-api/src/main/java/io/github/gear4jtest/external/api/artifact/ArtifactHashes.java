package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Provider-neutral SHA-256 helpers shared by artifact-store implementations.
 */
@io.github.gear4jtest.core.api.annotation.Internal
public final class ArtifactHashes {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private ArtifactHashes() {
    }

    public static String sha256Hex(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void requireContentIdentity(byte[] data,
                                              String expectedHash,
                                              long expectedSize,
                                              String description)
            throws IOException {
        Objects.requireNonNull(data, "data must not be null");
        Objects.requireNonNull(description, "description must not be null");
        if (data.length != expectedSize) {
            throw new IOException(description + " size mismatch: expected " + expectedSize + " but found "
                    + data.length);
        }
        requireSha256Match(expectedHash, sha256Hex(data), description);
    }

    public static void requireSha256Match(String expectedHash, String actualHash, String description)
            throws IOException {
        Objects.requireNonNull(description, "description must not be null");
        String expected;
        String actual;
        try {
            expected = requireSha256Hex(expectedHash);
            actual = requireSha256Hex(actualHash);
        } catch (IllegalArgumentException exception) {
            throw new IOException(description + " contains an invalid SHA-256 value", exception);
        }
        if (!expected.equals(actual)) {
            throw new IOException(description + " content hash mismatch: expected " + expected + " but found "
                    + actual);
        }
    }

    public static HashedStreamResult sha256Hex(InputStream in, long maxBytes) throws IOException {
        Objects.requireNonNull(in, "input stream must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (maxBytes >= 0 && total > maxBytes) {
                    throw new IOException("Artifact size exceeds configured limit. maxBytes=" + maxBytes);
                }
                digest.update(buffer, 0, read);
            }
            return new HashedStreamResult(HexFormat.of().formatHex(digest.digest()), total);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String requireSha256Hex(String hashHex) {
        if (hashHex == null || !SHA_256_HEX.matcher(hashHex).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 hex hash: " + hashHex);
        }
        return hashHex.toLowerCase(Locale.ROOT);
    }

    public record HashedStreamResult(String hashHex, long sizeBytes) {}
}
