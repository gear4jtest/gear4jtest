package io.github.gear4jtest.core.api.util;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition.EventConfiguration;

/**
 * Builders and shortcuts for Gear4J event runtime configuration.
 */
public final class Events {
    private Events() {
    }

    public static EventConfiguration.Builder eventConfiguration() {
        return new EventConfiguration.Builder();
    }

    public static EventHandlingDefinition.Builder eventHandling() {
        return new EventHandlingDefinition.Builder();
    }

    public static EventHandlingDefinition.RuntimeConfiguration waitForDrainRuntime() {
        return EventHandlingDefinition.RuntimeConfiguration.waitForDrainDefaults();
    }

    public static EventHandlingDefinition.RuntimeConfiguration detachAndDrainRuntime() {
        return EventHandlingDefinition.RuntimeConfiguration.detachAndDrainDefaults();
    }
}
