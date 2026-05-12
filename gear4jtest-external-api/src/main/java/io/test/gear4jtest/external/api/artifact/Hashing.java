package io.test.gear4jtest.external.api.artifact;

final class Hashing {
    private Hashing() {
    }

    static String sha256Hex(byte[] data) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            var d = md.digest(data);
            var sb = new StringBuilder(d.length * 2);
            for (byte b : d)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
