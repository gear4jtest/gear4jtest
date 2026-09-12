package io.github.gear4jtest.core.engine;

import java.util.UUID;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.RuntimeDefinitions;
import io.github.gear4jtest.core.event.EventRuntimeMetrics;
import io.github.gear4jtest.core.event.StationStartedEvent;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyLineEngineInitializationCleanupTest {
    @Test
    void contextPolicyFailure_shouldReleaseTheAcquiredEventRuntime() {
        // Given
        var failure = new IllegalArgumentException("context policy failed");
        var engine = AssemblyLineEngine.builder().resourceFactory(noResources())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .initialRunContextPolicy(context -> {
                    throw failure;
                })
                .executionContextRegistry(new ExecutionContextRegistry()).build();
        int before = EventRuntimeMetrics.snapshot().activeRuntimes();

        // When / Then
        assertThatThrownBy(() -> engine.execute(pipeline(), RunRequest.<String>builder().input("input").build()))
                .isSameAs(failure);
        assertThat(EventRuntimeMetrics.snapshot().activeRuntimes()).isEqualTo(before);
    }

    @Test
    void idGeneratorError_shouldEscapeAndReleaseTheEventRuntime() {
        // Given
        var failure = new AssertionError("id generator failed");
        var registry = new ExecutionContextRegistry();
        var engine = AssemblyLineEngine.builder().resourceFactory(noResources())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(registry).build();
        UUID id = UUID.randomUUID();
        int before = EventRuntimeMetrics.snapshot().activeRuntimes();

        // When / Then
        assertThatThrownBy(() -> engine.execute(pipeline(), RunRequest.<String>builder().input("input")
                .withIdGenerator(() -> {
                    throw failure;
                }).build())).isSameAs(failure);
        assertThat(EventRuntimeMetrics.snapshot().activeRuntimes()).isEqualTo(before);
        engine.execute(pipeline(), RunRequest.<String>builder().input("retry").withIdGenerator(() -> id).build());
        assertThat(registry.find(id)).isNull();
    }

    private static AssemblyLine<String, String> pipeline() {
        return AssemblyLines.<String>createAssemblyLine("initialization-failure")
                .configuration(RuntimeDefinitions.configuration().eventHandling(EventHandlingDefinition.builder()
                        .on(StationStartedEvent.class, event -> {
                        }).build()).build())
                .build();
    }

    private static ResourceFactory noResources() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                return null;
            }
        };
    }
}
