package io.github.gear4jtest.spring;

import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class Gear4jSpringConfigurationTest {
    @Test
    void should_wire_core_spring_beans() {
        // Given
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(SampleResource.class, SampleResource::new);
            context.register(Gear4jSpringConfiguration.class);

            // When
            context.refresh();

            // Then
            assertThat(context.getBean(ResourceFactory.class).getResource(SampleResource.class))
                    .as("resource factory should resolve Spring beans")
                    .isSameAs(context.getBean(SampleResource.class));
            assertThat(context.getBean(ExecutionContextRegistry.class))
                    .as("execution context registry")
                    .isNotNull();
            assertThat(context.getBean(PipelineEngine.class))
                    .as("pipeline engine")
                    .isNotNull();
            assertThat(context.getBean(AssemblyLineRegistry.class).getAll())
                    .as("assembly line registry should be available even when no pipeline beans exist")
                    .isEmpty();
        }
    }

    static final class SampleResource {
    }
}
