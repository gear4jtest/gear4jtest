package io.github.gear4jtest.core.spi.factory;

import java.util.UUID;

import io.github.gear4jtest.core.util.DefaultUuidGenerator;

/**
 * Extension point used to generate run and station identifiers.
 *
 * <p>
 * Implementations are called from the execution engine and may be invoked
 * concurrently. Custom implementations must return non-null identifiers and
 * should make collisions practically impossible within the repository namespace
 * they write to.
 * </p>
 *
 * <p>
 * The default implementation is a dependency-free, thread-safe UUIDv7
 * generator. During clock rollback or a frozen clock, it advances a per-thread
 * logical timestamp after the UUIDv7 sequence is exhausted instead of waiting
 * for wall time to recover.
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

    /**
     * Generates a new unique identifier for a run or station log.
     */
    UUID generate();
}
