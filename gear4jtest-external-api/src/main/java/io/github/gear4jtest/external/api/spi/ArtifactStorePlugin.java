package io.github.gear4jtest.external.api.spi;

import java.util.Map;

import io.github.gear4jtest.external.api.artifact.ArtifactStore;

public interface ArtifactStorePlugin {
    /**
     * Returns the canonical store type handled by this plugin, for example
     * {@code S3} or {@code MEMORY}.
     */
    String type();

    /**
     * Builds a store from string properties without exposing backend-specific types
     * in the SPI signature.
     */
    ArtifactStore build(Map<String, String> props, Context ctx) throws Exception;

    /**
     * Generic context used to look up optional backend resources such as data
     * sources or clients.
     */
    interface Context {
        /**
         * Looks up an optional resource by key without coupling the API to a concrete
         * backend type.
         */
        Object lookup(String key);

        default void warn(String msg) {
        }

        default void info(String msg) {
        }
    }
}
