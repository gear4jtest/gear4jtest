package io.github.gear4jtest.studio.model;

/**
 * Domain-separated HMAC-SHA256 of exact UTF-8 fixture bytes, produced by
 * ResolvedScenarioInput. Comparable only within the same keyId.
 */
public record FixtureFingerprint(String keyId, String value) {
    public FixtureFingerprint {
        if (keyId == null || !keyId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                || value == null || !value.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid fixture fingerprint");
    }
}
