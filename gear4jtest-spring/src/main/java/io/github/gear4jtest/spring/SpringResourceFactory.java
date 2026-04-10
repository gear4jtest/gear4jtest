package io.github.gear4jtest.spring;

import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import java.util.Objects;
import org.springframework.context.ApplicationContext;

/**
 * {@link ResourceFactory} implementation backed by Spring's {@link ApplicationContext}.
 *
 * <p>This allows Gear4J operators, processors or any other engine resource to be resolved
 * directly from the Spring container.
 */
public final class SpringResourceFactory implements ResourceFactory {

    private final ApplicationContext applicationContext;

    public SpringResourceFactory(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
    }

    @Override
    public <T> T getResource(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }
}
