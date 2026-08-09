package io.github.gear4jtest.external.api.loader;

import io.github.gear4jtest.core.api.annotation.Spi;

/**
 * Registry for generated classloaders and mutable aliases such as
 * {@code al/<id>/RUN/latest}.
 */
@Spi
public interface ClassLoaderRegistry {
    ClassLoader get(String internalLoaderId);

    /**
     * Registers a loader whose bytecode weight can be derived from Gear4J's
     * in-memory loader. Custom loaders should use the weighted overload.
     */
    default void register(String internalLoaderId, ClassLoader loader, GeneratedAssemblyLine<?, ?> bound) {
        long bytecodeWeightBytes = loader instanceof InMemoryClassLoader inMemory
                ? inMemory.bytecodeWeightBytes()
                : 0L;
        register(internalLoaderId, loader, bound, bytecodeWeightBytes);
    }

    /**
     * Registers a generated classloader with the cumulative bytecode weight of the
     * compilation it owns.
     *
     * <p>
     * Implementations must apply their configured hard weight limit before
     * retaining the loader. The weight remains conservative after individual class
     * byte arrays have been released because defined classes still occupy
     * metaspace.
     * </p>
     */
    default void register(String internalLoaderId,
                          ClassLoader loader,
                          GeneratedAssemblyLine<?, ?> bound,
                          long bytecodeWeightBytes) {
        register(internalLoaderId, loader, bound, bytecodeWeightBytes, RegistrationLease.published());
    }

    /**
     * Stages a generated classloader under a lease controlled by the loading
     * runtime.
     *
     * <p>
     * Implementations must retain the lease with the entry and must not expose the
     * classloader, bound assembly line or aliases until
     * {@link RegistrationLease#isPublished()} returns {@code true}. This makes a
     * registration that returns after its deadline invisible while it is being
     * discarded.
     * </p>
     */
    void register(String internalLoaderId,
                  ClassLoader loader,
                  GeneratedAssemblyLine<?, ?> bound,
                  long bytecodeWeightBytes,
                  RegistrationLease registrationLease);

    void evict(String internalLoaderId);

    /**
     * Evicts a loader only when the current entry is still owned by the expected
     * classloader instance.
     *
     * <p>
     * The ownership check and eviction must be atomic. Generated loading uses this
     * operation to discard a registration that completed after its end-to-end
     * deadline without deleting a newer successful retry for the same identifier.
     * Implementations should make the operation idempotent and return {@code false}
     * when the identifier is absent or has already been replaced.
     * </p>
     *
     * @return {@code true} when the expected entry was evicted
     */
    boolean evictIfOwned(String internalLoaderId, ClassLoader expectedLoader);

    void setAlias(String alias, String internalLoaderId);

    default void clearAlias(String alias) {
        setAlias(alias, null);
    }

    String resolveAlias(String alias);

    GeneratedAssemblyLine<?, ?> getBoundAssemblyLine(String internalLoaderId);

    /**
     * Runtime-owned visibility lease for one generated classloader registration.
     */
    @FunctionalInterface
    interface RegistrationLease {
        boolean isPublished();

        static RegistrationLease published() {
            return () -> true;
        }
    }
}
