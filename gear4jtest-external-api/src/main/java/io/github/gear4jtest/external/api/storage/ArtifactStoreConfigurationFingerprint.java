package io.github.gear4jtest.external.api.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.external.api.model.OperationChainConfig;

/**
 * Computes a stable fingerprint for the artifact-store portion of an operation
 * chain configuration.
 */
public final class ArtifactStoreConfigurationFingerprint {
    public static final String UNSPECIFIED = "0".repeat(64);

    private ArtifactStoreConfigurationFingerprint() {
    }

    public static String from(OperationChainConfig config) {
        OperationChainConfig requiredConfig = Objects.requireNonNull(config, "config must not be null");
        MessageDigest digest = sha256();
        update(digest, requiredConfig.storeType().name());
        requiredConfig.storeProps().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    update(digest, entry.getKey());
                    update(digest, entry.getValue());
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "store configuration values must not be null")
                .getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
