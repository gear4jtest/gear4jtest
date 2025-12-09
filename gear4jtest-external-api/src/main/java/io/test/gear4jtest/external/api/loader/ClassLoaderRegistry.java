package io.test.gear4jtest.external.api.loader;

public interface ClassLoaderRegistry {
    ClassLoader get(String internalLoaderId);

    void register(String internalLoaderId, ClassLoader loader, GeneratedAssemblyLine bound);

    void evict(String internalLoaderId);

    void setAlias(String alias, String internalLoaderId);

    String resolveAlias(String alias);

    GeneratedAssemblyLine getBoundAssemblyLine(String internalLoaderId);
}
