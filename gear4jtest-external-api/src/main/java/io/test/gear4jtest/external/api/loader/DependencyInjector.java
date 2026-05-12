package io.test.gear4jtest.external.api.loader;

import java.util.Optional;

import io.test.gear4jtest.external.api.ExecutionMode;

public interface DependencyInjector {

    /**
     * Injects registered dependencies into the supplied instance.
     */
    void injectDependencies(Object instance, ExecutionMode mode) throws InjectionException;

    /**
     * Registers a singleton bean.
     */
    void registerBean(String name, Object bean);

    /**
     * Registers a bean with an explicit scope.
     */
    void registerBean(String name, Object bean, BeanScope scope);

    /**
     * Returns a registered bean matching the requested name and type.
     */
    <T> Optional<T> getBean(String name, Class<T> type);

    /**
     * Lifetime of a registered bean.
     */
    enum BeanScope {
        SINGLETON, PROTOTYPE, REQUEST, SESSION
    }

    /**
     * Raised when dependency injection cannot complete.
     */
    class InjectionException extends Exception {
        public InjectionException(String message) {
            super(message);
        }

        public InjectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
