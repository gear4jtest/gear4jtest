package io.github.gear4jtest.studio.demo;

import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.model.ScenarioData;
import io.github.gear4jtest.studio.model.ScenarioInput;
import io.github.gear4jtest.studio.spi.ScenarioDataPolicy;

/**
 * Demo fixtures only. Hosts must implement domain-specific admission, not
 * accept arbitrary "synthetic" text.
 */
public final class DemoScenarioPolicy implements ScenarioDataPolicy {
    private static final Set<String> INPUTS = Set.of("", "hello", "  hello  ", "world");
    private static final Set<String> OUTPUTS = Set.of("", "hello", "HELLO", "world", "WORLD");

    @Override
    public void assertSafeToStore(StudioIdentity actor, UUID scope, ScenarioData data) {
        resolveInput(actor, scope, data.input());
        if (data.expected() != null && data.expected().output() != null && !OUTPUTS.contains(data.expected().output()))
            throw new IllegalArgumentException("Fixture not admitted");
    }

    @Override
    public String resolveInput(StudioIdentity actor, UUID scope, ScenarioInput input) {
        if (input.kind() == ScenarioInput.Kind.SYNTHETIC_TEXT && INPUTS.contains(input.value()))
            return input.value();
        if (input.kind() == ScenarioInput.Kind.FIXTURE_REFERENCE && input.value().equals("demo/greeting")
                && input.fixtureVersion().equals("1"))
            return "  hello  ";
        throw new IllegalArgumentException("Fixture not admitted");
    }
}
