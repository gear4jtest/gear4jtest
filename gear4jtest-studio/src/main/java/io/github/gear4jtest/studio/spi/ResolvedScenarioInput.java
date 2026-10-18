package io.github.gear4jtest.studio.spi;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import io.github.gear4jtest.studio.model.FixtureFingerprint;

/**
 * Transient host result. The factory binds the fingerprint to the exact value;
 * neither normalization nor a caller-supplied digest is accepted. The host owns
 * a stable secret key and a globally unambiguous keyId, changed on key
 * rotation. Values must never be logged or persisted as part of this object.
 */
public final class ResolvedScenarioInput {
    private final String value;
    private final FixtureFingerprint fingerprint;

    private ResolvedScenarioInput(String value, FixtureFingerprint fingerprint) {
        this.value = value;
        this.fingerprint = fingerprint;
    }

    public static ResolvedScenarioInput hmacSha256(String value, String keyId, SecretKey key) {
        if (value == null || value.length() > 8192 || !StandardCharsets.UTF_8.newEncoder().canEncode(value))
            throw new IllegalArgumentException("Invalid resolved fixture");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(java.util.Objects.requireNonNull(key));
            mac.update("gear4j-scenario-input-v1\0".getBytes(StandardCharsets.UTF_8));
            var digest = HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
            return new ResolvedScenarioInput(value, new FixtureFingerprint(keyId, digest));
        } catch (java.security.GeneralSecurityException unavailable) {
            throw new IllegalArgumentException("Fixture fingerprint unavailable", unavailable);
        }
    }

    public String value() {
        return value;
    }

    public FixtureFingerprint fingerprint() {
        return fingerprint;
    }

    @Override
    public String toString() {
        return "ResolvedScenarioInput[value=not disclosed]";
    }
}
