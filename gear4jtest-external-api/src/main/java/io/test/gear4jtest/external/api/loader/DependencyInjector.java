package io.test.gear4jtest.external.api.loader;

import java.util.Optional;

import io.test.gear4jtest.external.api.ExecutionMode;

public interface DependencyInjector {
    
    /**
     * Injecte les dépendances dans une instance
     */
    void injectDependencies(Object instance, ExecutionMode mode) throws InjectionException;
    
    /**
     * Enregistre un bean dans le contexte
     */
    void registerBean(String name, Object bean);
    
    /**
     * Enregistre un bean avec un scope spécifique
     */
    void registerBean(String name, Object bean, BeanScope scope);
    
    /**
     * Récupère un bean du contexte
     */
    <T> Optional<T> getBean(String name, Class<T> type);
    
    /**
     * Scope des beans
     */
    enum BeanScope {
        SINGLETON, PROTOTYPE, REQUEST, SESSION
    }
    
    /**
     * Exception d'injection
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