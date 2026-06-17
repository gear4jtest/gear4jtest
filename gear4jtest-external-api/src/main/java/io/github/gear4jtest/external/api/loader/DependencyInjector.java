package io.github.gear4jtest.external.api.loader;

import java.util.Optional;

import io.github.gear4jtest.core.api.annotation.Spi;
import io.github.gear4jtest.external.api.ExecutionMode;

@Spi
public interface DependencyInjector {
    /**
     * Injects registered dependencies into the supplied instance.
     */
    void injectDependencies(Object instance, ExecutionMode mode) throws InjectionException;

    /**
     * Registers a named singleton bean instance.
     *
     * <p>
     * The lightweight external injector does not implement scopes. Callers that
     * need prototype, request or session semantics should adapt Gear4J to their DI
     * container and expose the resolved dependency as a concrete instance here.
     * </p>
     */
    void registerBean(String name, Object bean);

    /**
     * Returns a registered bean matching the requested name and type.
     */
    <T> Optional<T> getBean(String name, Class<T> type);

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
