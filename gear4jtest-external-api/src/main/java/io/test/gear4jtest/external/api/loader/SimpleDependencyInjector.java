package io.test.gear4jtest.external.api.loader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.test.gear4jtest.external.api.ExecutionMode;

public class SimpleDependencyInjector implements DependencyInjector {
    private final Map<String, BeanDefinition> beans = new ConcurrentHashMap<>();

    @Override
    public void injectDependencies(Object instance, ExecutionMode mode) throws InjectionException {
        Class<?> clazz = instance.getClass();

        // Injection par annotations (exemple avec des annotations custom)
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            if (field.isAnnotationPresent(Inject.class)) {
                Inject inject = field.getAnnotation(Inject.class);
                String beanName = inject.value().isEmpty() ? field.getName() : inject.value();

                Optional<?> bean = getBean(beanName, field.getType());
                if (bean.isPresent()) {
                    try {
                        field.setAccessible(true);
                        field.set(instance, bean.get());
                    } catch (IllegalAccessException e) {
                        throw new InjectionException("Erreur d'injection pour le champ: " + field.getName(), e);
                    }
                } else if (inject.required()) {
                    throw new InjectionException("Bean requis non trouvé: " + beanName);
                }
            }
        }
    }

    @Override
    public void registerBean(String name, Object bean) {
        registerBean(name, bean, BeanScope.SINGLETON);
    }

    @Override
    public void registerBean(String name, Object bean, BeanScope scope) {
        beans.put(name, new BeanDefinition(bean, scope));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getBean(String name, Class<T> type) {
        BeanDefinition definition = beans.get(name);
        if (definition == null) {
            return Optional.empty();
        }

        Object bean = definition.getInstance();
        if (type.isInstance(bean)) {
            return Optional.of((T) bean);
        }

        return Optional.empty();
    }

    /**
     * Définition d'un bean
     */
    private static class BeanDefinition {
        private final Object instance;
        private final BeanScope scope;

        public BeanDefinition(Object instance, BeanScope scope) {
            this.instance = instance;
            this.scope = scope;
        }

        public Object getInstance() {
            // Pour simplifier, on retourne toujours la même instance
            // Dans une vraie implémentation, on gérerait les différents scopes
            return instance;
        }

        public BeanScope getScope() {
            return scope;
        }
    }
}
