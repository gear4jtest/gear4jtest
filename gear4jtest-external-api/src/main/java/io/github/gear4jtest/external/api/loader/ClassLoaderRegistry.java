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
    void register(String internalLoaderId,
                  ClassLoader loader,
                  GeneratedAssemblyLine<?, ?> bound,
                  long bytecodeWeightBytes);

    void evict(String internalLoaderId);

    void setAlias(String alias, String internalLoaderId);

    default void clearAlias(String alias) {
        setAlias(alias, null);
    }

    String resolveAlias(String alias);

    GeneratedAssemblyLine<?, ?> getBoundAssemblyLine(String internalLoaderId);
}
