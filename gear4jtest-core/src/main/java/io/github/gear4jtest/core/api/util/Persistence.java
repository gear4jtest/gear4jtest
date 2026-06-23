package io.github.gear4jtest.core.api.util;

import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.execution.AssemblyRunManager;

/**
 * Builders for Gear4J persistence configuration and built-in persistence
 * extensions.
 */
public final class Persistence {
    private Persistence() {
    }

    public static PersistenceConfiguration.Builder persistenceConfiguration() {
        return new PersistenceConfiguration.Builder();
    }

    public static PersistenceExtension.Builder persistenceExtension(AssemblyRunManager manager) {
        return PersistenceExtension.builder(manager);
    }
}
