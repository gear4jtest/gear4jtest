package io.github.gear4jtest.spring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;
import org.springframework.beans.factory.ListableBeanFactory;

/**
 * Default Spring-backed {@link AssemblyLineRegistry}.
 */
public final class SpringAssemblyLineRegistry implements AssemblyLineRegistry {
    private final Map<String, AssemblyLine<?, ?>> assemblyLinesByBeanName;

    @SuppressWarnings("rawtypes")
    public SpringAssemblyLineRegistry(ListableBeanFactory beanFactory) {
        Objects.requireNonNull(beanFactory, "beanFactory must not be null");

        Map<String, AssemblyLine> discovered = beanFactory.getBeansOfType(AssemblyLine.class);
        Map<String, AssemblyLine<?, ?>> ordered = new LinkedHashMap<>();
        discovered.forEach(ordered::put);
        this.assemblyLinesByBeanName = Map.copyOf(ordered);
    }

    @Override
    public List<AssemblyLine<?, ?>> getAll() {
        return new ArrayList<>(assemblyLinesByBeanName.values());
    }

    @Override
    public Optional<AssemblyLine<?, ?>> findByBeanName(String beanName) {
        return Optional.ofNullable(assemblyLinesByBeanName.get(beanName));
    }
}
