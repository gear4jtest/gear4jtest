package io.github.gear4jtest.external.api.artifact;

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

    static String requireSha256Hex(String hashHex) {
        if (hashHex == null || !SHA_256_HEX.matcher(hashHex).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 hex hash: " + hashHex);
        }
        return hashHex.toLowerCase(Locale.ROOT);
    }
}
