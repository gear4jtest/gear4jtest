package io.github.gear4jtest.studio.runtime;

import io.github.gear4jtest.studio.service.InMemoryStudioService;
import io.github.gear4jtest.studio.spi.StudioAuthorization;

/**
 * Retired P0 harness, only for adapter regression tests; never shipped in
 * production.
 */
final class LegacyStudioFixture {
    private LegacyStudioFixture() {
    }

    public static StudioAuthorization authorization() {
        return (actor, action) -> {
            if (!"demo-editor".equals(actor)
                    && !("demo-viewer".equals(actor) && action == StudioAuthorization.Action.READ))
                throw new SecurityException("Action denied");
        };
    }

    public static InMemoryStudioService service(LocalGear4jRuntime runtime) {
        return new InMemoryStudioService(runtime, authorization());
    }

}
