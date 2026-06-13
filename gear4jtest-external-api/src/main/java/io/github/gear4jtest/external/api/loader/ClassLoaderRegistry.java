package io.github.gear4jtest.external.api.loader;

/**
 * Registry for generated classloaders and mutable aliases such as
 * {@code al/<id>/RUN/latest}.
 */
public interface ClassLoaderRegistry {
    ClassLoader get(String internalLoaderId);

    void register(String internalLoaderId, ClassLoader loader, GeneratedAssemblyLine bound);

    void evict(String internalLoaderId);

    void setAlias(String alias, String internalLoaderId);

    default void clearAlias(String alias) {
        setAlias(alias, null);
    }

    String resolveAlias(String alias);

    GeneratedAssemblyLine getBoundAssemblyLine(String internalLoaderId);
}
