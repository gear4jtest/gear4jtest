package io.github.gear4jtest.external.api.loader;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.gear4jtest.external.api.ExecutionMode;

import static java.util.Objects.requireNonNull;

public class SimpleDependencyInjector implements DependencyInjector {
    private final Map<String, RegisteredBean> beans = new ConcurrentHashMap<>();

    /**
     * Registers a bean for {@link ExecutionMode#RUN} only.
     *
     * <p>
     * TEST artifacts must opt in explicitly with
     * {@link #registerBean(String, Object, ExecutionMode, ExecutionMode...)} so a
     * draft or unpromoted generated assembly line does not automatically get access
     * to every dependency known by this lightweight injector.
     * </p>
     */
    @Override
    public void registerBean(String name, Object bean) {
        registerBean(name, bean, ExecutionMode.RUN);
    }

    /**
     * Registers a bean with an explicit execution-mode allowlist.
     */
    public void registerBean(String name, Object bean, ExecutionMode firstMode, ExecutionMode... additionalModes) {
        requireNonNull(firstMode, "firstMode must not be null");
        EnumSet<ExecutionMode> allowedModes = EnumSet.of(firstMode);
        if (additionalModes != null) {
            for (ExecutionMode mode : additionalModes) {
                allowedModes.add(requireNonNull(mode, "additional mode must not be null"));
            }
        }
        registerBean(name, bean, allowedModes);
    }

    /**
     * Registers a bean with an explicit execution-mode allowlist.
     */
    public void registerBean(String name, Object bean, Set<ExecutionMode> allowedModes) {
        requireNonNull(name, "name must not be null");
        requireNonNull(bean, "bean must not be null");
        requireNonNull(allowedModes, "allowedModes must not be null");
        if (allowedModes.isEmpty()) {
            throw new IllegalArgumentException("allowedModes must not be empty");
        }
        beans.put(name, new RegisteredBean(bean, EnumSet.copyOf(allowedModes)));
    }

    @Override
    public void injectDependencies(Object instance, ExecutionMode mode) throws InjectionException {
        requireNonNull(instance, "instance must not be null");
        requireNonNull(mode, "mode must not be null");
        Class<?> clazz = instance.getClass();

        // Annotation-based injection using the external API annotations.
        java.lang.reflect.Field[] fields = clazz.getDeclaredFields();
        for (java.lang.reflect.Field field : fields) {
            if (field.isAnnotationPresent(Inject.class)) {
                Inject inject = field.getAnnotation(Inject.class);
                String beanName = inject.value().isEmpty() ? field.getName() : inject.value();

                InjectionCandidate candidate = findCandidate(beanName, field.getType(), mode);
                if (candidate.bean().isPresent()) {
                    try {
                        field.setAccessible(true);
                        field.set(instance, candidate.bean().get());
                    } catch (IllegalAccessException e) {
                        throw new InjectionException("Dependency injection failed for field: " + field.getName(), e);
                    }
                } else if (inject.required()) {
                    if (candidate.disallowedForMode()) {
                        throw new InjectionException("Bean '" + beanName + "' is not allowed in " + mode
                                + " mode for field: " + field.getName());
                    }
                    throw new InjectionException("Required bean not found: " + beanName);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getBean(String name, Class<T> type) {
        requireNonNull(name, "name must not be null");
        requireNonNull(type, "type must not be null");
        RegisteredBean registeredBean = beans.get(name);
        if (registeredBean == null || !type.isInstance(registeredBean.bean())) {
            return Optional.empty();
        }
        return Optional.of((T) registeredBean.bean());
    }

    @SuppressWarnings("unchecked")
    private <T> InjectionCandidate findCandidate(String name, Class<T> type, ExecutionMode mode) {
        RegisteredBean registeredBean = beans.get(name);
        if (registeredBean == null) {
            return InjectionCandidate.notFound();
        }
        if (!registeredBean.allowedModes().contains(mode)) {
            return InjectionCandidate.disallowed();
        }
        if (!type.isInstance(registeredBean.bean())) {
            return InjectionCandidate.notFound();
        }
        return InjectionCandidate.found(Optional.of((T) registeredBean.bean()));
    }

    private record RegisteredBean(Object bean, EnumSet<ExecutionMode> allowedModes) {
        private RegisteredBean {
            allowedModes = EnumSet.copyOf(allowedModes);
        }
    }

    private record InjectionCandidate(Optional<?> bean, boolean disallowedForMode) {
        private static InjectionCandidate found(Optional<?> bean) {
            return new InjectionCandidate(bean, false);
        }

        private static InjectionCandidate notFound() {
            return new InjectionCandidate(Optional.empty(), false);
        }

        private static InjectionCandidate disallowed() {
            return new InjectionCandidate(Optional.empty(), true);
        }
    }
}
