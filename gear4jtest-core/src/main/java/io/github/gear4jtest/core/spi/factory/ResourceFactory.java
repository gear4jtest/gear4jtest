package io.github.gear4jtest.core.spi.factory;

/**
 * Resolves runtime resources requested by stations or processors.
 *
 * <p>
 * Implementations are called during pipeline execution and may therefore be
 * invoked concurrently by different runs or parallel branches. Implementations
 * should either be stateless or provide their own thread-safety. Returning
 * {@code null} means that the requested resource is not available.
 * </p>
 */
@FunctionalInterface
public interface ResourceFactory {
    /**
     * Returns a resource assignable to {@code clazz}, or {@code null} if no such
     * resource exists.
     */
    <T> T getResource(Class<T> clazz);
}
