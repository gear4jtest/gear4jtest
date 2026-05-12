package io.github.gear4jtest.spring;

import java.util.List;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;

/**
 * Spring-side registry of {@link AssemblyLine} beans.
 *
 * <p>
 * This registry is useful for diagnostics, administration, testing, or for
 * selecting a pipeline by bean name in an application service layer.
 */
public interface AssemblyLineRegistry {

    List<AssemblyLine<?, ?>> getAll();

    Optional<AssemblyLine<?, ?>> findByBeanName(String beanName);

    default AssemblyLine<?, ?> getByBeanName(String beanName) {
        return findByBeanName(beanName)
                .orElseThrow(() -> new IllegalArgumentException("No AssemblyLine bean found with name: " + beanName));
    }
}
