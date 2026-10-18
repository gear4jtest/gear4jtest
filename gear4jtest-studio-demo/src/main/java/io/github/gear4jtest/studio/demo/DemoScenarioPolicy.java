package io.github.gear4jtest.studio.demo;

import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.model.ScenarioData;
import io.github.gear4jtest.studio.model.ScenarioInput;
import io.github.gear4jtest.studio.spi.ResolvedScenarioInput;
import io.github.gear4jtest.studio.spi.ScenarioDataPolicy;

/**
 * Demo fixtures only. Hosts must implement domain-specific admission, not
 * accept arbitrary "synthetic" text.
 */
public final class DemoScenarioPolicy implements ScenarioDataPolicy {
    private static final Set<String> INPUTS = Set.of("", "hello", "  hello  ", "world");
    private static final Set<String> OUTPUTS = Set.of("", "hello", "HELLO", "world", "WORLD");
    private final javax.crypto.SecretKey fingerprintKey;
    private final String fingerprintKeyId = "demo-" + UUID.randomUUID();

    public DemoScenarioPolicy() {
        try {
            var generator = javax.crypto.KeyGenerator.getInstance("HmacSHA256");
            generator.init(256);
            fingerprintKey = generator.generateKey();
        } catch (java.security.GeneralSecurityException unavailable) {
            throw new IllegalStateException("HMAC unavailable", unavailable);
        }
    }

    @Override
    public void assertSafeToStore(StudioIdentity actor, UUID scope, ScenarioData data) {
        var input = data.input();
        if (input.kind() == ScenarioInput.Kind.SYNTHETIC_TEXT ? !INPUTS.contains(input.value())
                : !input.value().equals("demo/greeting") || !input.fixtureVersion().equals("1"))
            throw new IllegalArgumentException("Fixture not admitted");
        if (data.expected() != null && data.expected().output() != null && !OUTPUTS.contains(data.expected().output()))
            throw new IllegalArgumentException("Fixture not admitted");
    }

    @Override
    public ResolvedScenarioInput resolveFixture(StudioIdentity actor, UUID scope, ScenarioInput input) {
        if (input.kind() == ScenarioInput.Kind.FIXTURE_REFERENCE && input.value().equals("demo/greeting")
                && input.fixtureVersion().equals("1"))
            return ResolvedScenarioInput.hmacSha256("  hello  ", fingerprintKeyId, fingerprintKey);
        throw new IllegalArgumentException("Fixture not admitted");
    }
}
