package io.github.gear4jtest.core.sidecompute;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.event.Event;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.AssemblyRunRecord;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class SideComputeCustomEventTest {

    @Test
    void sideComputer_shouldSupportCustomUserEvents() {
        ExecutionContextRegistry registry = new ExecutionContextRegistry();

        SideComputer<CustomEvent, String, String> sideComputer = SideComputer
                .<CustomEvent, String>onEvent(CustomEvent.class, "custom-key")
                .computer(CustomEvent::getPayload)
                .map(String::toUpperCase)
                .build();

        EventManager eventManager = new EventManager(
                EventHandlingDefinition.builder()
                        .sideComputer(sideComputer)
                        .runtimeConfiguration(EventHandlingDefinition.RuntimeConfiguration.builder()
                                .reactionExecutorFactory(Executors::newSingleThreadExecutor)
                                .shutdownTimeout(Duration.ofSeconds(2))
                                .build())
                        .build(),
                registry);

        ExecutionContext executionContext = new ExecutionContext(
                UUID.randomUUID(),
                "pipe",
                new ExecutionServices(eventManager, new NoOpResourceFactory()),
                new AssemblyRunTrace(UUID.randomUUID(), "pipe", Map.of()));
        registry.register(executionContext);

        try {
            eventManager.publish(new CustomEvent("pipe", executionContext.getExecutionId(), "hello"));

            assertThat(executionContext.getSideComputeContext().<String>getOrCreateFuture("custom-key").join())
                    .isEqualTo("HELLO");
        } finally {
            eventManager.shutdown();
            registry.remove(executionContext.getExecutionId());
        }
    }

    private static final class CustomEvent extends Event {
        private final String payload;

        private CustomEvent(String pipelineId, UUID executionId, String payload) {
            super(pipelineId, executionId);
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }
    }

    private static final class NoOpResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }
}
