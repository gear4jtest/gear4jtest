package io.github.gear4jtest.core.api.context;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

final class ExecutionServicesTest {
    @Test
    void getEventPublisher_shouldExposeStableViewOfRuntimeManager() {
        // Given
        EventManager eventManager = new EventManager(EventHandlingDefinition.builder().build(),
                new ExecutionContextRegistry());
        ExecutionServices services = new ExecutionServices(eventManager, noResources());

        // When / Then
        assertThat(services.getEventPublisher()).isSameAs(eventManager);
    }

    @Test
    void getEventPublisher_shouldReturnNoOpWhenEventsAreDisabled() {
        // Given
        ExecutionServices services = new ExecutionServices(null, noResources());
        Event event = new Event("assembly-line", null);

        // When / Then
        assertThatCode(() -> services.getEventPublisher().publish(event)).doesNotThrowAnyException();
    }

    private static ResourceFactory noResources() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
    }
}
