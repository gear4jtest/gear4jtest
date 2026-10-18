package io.github.gear4jtest.studio.spi;

import java.util.UUID;

import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.model.ScenarioData;
import io.github.gear4jtest.studio.model.ScenarioInput;

/**
 * Host-owned fixture admission and immutable resolution. Distinct from capture
 * policy: only synthetic/non-sensitive fixtures are allowed; no credentials or
 * secret configuration references. Check the current actor and reference
 * version again on each execution. Never redact/normalize data silently before
 * storage.
 */
public interface ScenarioDataPolicy {
    void assertSafeToStore(StudioIdentity actor, UUID scope, ScenarioData data);

    /**
     * Explicit opt-in: existing fixture policies do not implicitly admit mock
     * payloads.
     */
    default boolean simulationEnabled() {
        return false;
    }

    /**
     * Admission only, never rewrite synthetic mock values. Called on save and
     * simulation.
     */
    default void assertSafeSimulationToStore(StudioIdentity actor,
                                             UUID scope,
                                             io.github.gear4jtest.studio.model.SimulationProfile profile) {
        throw new SecurityException("Simulation data is not admitted");
    }

    /**
     * Resolve a FIXTURE_REFERENCE only, outside management transactions. Studio
     * never calls this for SYNTHETIC_TEXT: admission may refuse that text, but the
     * persisted value is replayed verbatim. The returned fingerprint is bound to
     * the resolved content and recorded before execution.
     */
    ResolvedScenarioInput resolveFixture(StudioIdentity actor, UUID scope, ScenarioInput reference);
}
