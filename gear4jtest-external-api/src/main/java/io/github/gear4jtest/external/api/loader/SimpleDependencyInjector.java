package io.github.gear4jtest.external.api.loader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.external.api.ExecutionMode;

public class SimpleDependencyInjector implements DependencyInjector {
    private final Map<String, Object> beans = new ConcurrentHashMap<>();

    @Override
    public void injectDependencies(Object instance, ExecutionMode mode) throws InjectionException {
        Class<?> clazz = instance.getClass();

        // Annotation-based injection using the external API annotations.
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
                        throw new InjectionException("Dependency injection failed for field: " + field.getName(), e);
                    }
                } else if (inject.required()) {
                    throw new InjectionException("Required bean not found: " + beanName);
                }
            }
        }
    }

    @Override
    public void registerBean(String name, Object bean) {
        beans.put(name, bean);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getBean(String name, Class<T> type) {
        Object bean = beans.get(name);
        if (bean == null || !type.isInstance(bean)) {
            return Optional.empty();
        }
        return Optional.of((T) bean);
    }
}
