package io.github.gear4jtest.core.spi.factory;

import java.util.UUID;

import io.github.gear4jtest.core.util.DefaultUuidGenerator;

/**
 * Extension point used to generate run and station identifiers.
 *
 * <p>
 * The default implementation is a dependency-free, thread-safe UUIDv7
 * generator.
 * </p>
 */
@FunctionalInterface
public interface IdGenerator {

    /**
     * Returns the default UUIDv7 generator.
     */
    static IdGenerator defaultGenerator() {
        return DefaultUuidGenerator::generate;
    }

    UUID generate();
}
