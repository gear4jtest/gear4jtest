package io.github.gear4jtest.core.api.util;

import io.github.gear4jtest.core.api.AssemblyLine.Configuration;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition.EventConfiguration;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;

/**
 * Builders for pipeline-level runtime configuration.
 */
public final class RuntimeDefinitions {
    private RuntimeDefinitions() {
    }

    public static EventConfiguration.Builder eventConfiguration() {
        return new EventConfiguration.Builder();
    }

    public static EventHandlingDefinition.Builder eventHandling() {
        return new EventHandlingDefinition.Builder();
    }

    public static Configuration.Builder configuration() {
        return new Configuration.Builder();
    }

    public static PersistenceConfiguration.Builder persistenceConfiguration() {
        return new PersistenceConfiguration.Builder();
    }
}
