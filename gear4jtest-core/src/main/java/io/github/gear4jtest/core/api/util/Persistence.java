package io.github.gear4jtest.core.api.util;

import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.builtin.extension.PersistenceExtension;
import io.github.gear4jtest.core.persistence.RunPersistenceManager;

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

    public static PersistenceExtension persistenceExtension(RunPersistenceManager manager) {
        return new PersistenceExtension(manager);
    }
}
