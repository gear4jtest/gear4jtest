package io.github.gear4jtest.studio.model;

/**
 * Durable provenance of a resolved fixture; contains neither the input nor the
 * HMAC key.
 */
public record ResolvedFixture(String reference, String version, FixtureFingerprint fingerprint) {
    public ResolvedFixture {
        new ScenarioInput(ScenarioInput.Kind.FIXTURE_REFERENCE, reference, version);
        java.util.Objects.requireNonNull(fingerprint);
    }
}
