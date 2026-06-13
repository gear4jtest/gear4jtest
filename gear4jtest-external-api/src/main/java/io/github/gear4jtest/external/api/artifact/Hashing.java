package io.github.gear4jtest.external.api.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class Hashing {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private Hashing() {
    }

    static String sha256Hex(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static HashedStreamResult sha256Hex(InputStream in, long maxBytes) throws IOException {
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

    static String requireSha256Hex(String hashHex) {
        if (hashHex == null || !SHA_256_HEX.matcher(hashHex).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 hex hash: " + hashHex);
        }
        return hashHex.toLowerCase(Locale.ROOT);
    }

    record HashedStreamResult(String hashHex, long sizeBytes) {}
}
