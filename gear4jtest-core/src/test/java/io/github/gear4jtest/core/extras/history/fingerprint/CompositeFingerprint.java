package io.github.gear4jtest.core.extras.history.fingerprint;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public final class CompositeFingerprint {
    private final List<byte[]> parts = new ArrayList<>();

    private static byte[] intToBytes(int v) {
        return java.nio.ByteBuffer.allocate(4).putInt(v).array();
    }

    public CompositeFingerprint add(byte[] bytes) {
        if (bytes != null) {
            parts.add(bytes);
        }
        return this;
    }

    public CompositeFingerprint addUtf8(String s) {
        if (s != null) {
            parts.add(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return this;
    }

    public byte[] sha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] p : parts) {
                // delimiter to reduce ambiguity (length prefix)
                digest.update(intToBytes(p.length));
                digest.update(p);
            }
            return digest.digest();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute composite SHA-256 fingerprint", e);
        }
    }
}
