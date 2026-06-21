package io.github.gear4jtest.core.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.api.pipeline.NestedRunContext;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImmutableRequestAndPipelineTest {
    @Test
    void runRequest_shouldDefensivelyCopyAndExposeReadOnlyContext() {
        // Given
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("initial", "value");

        // When
        RunRequest request = RunRequest.builder().context(context).build();
        context.put("late", "mutation");

        // Then
        assertThat(request.getContext()).containsEntry("initial", "value").doesNotContainKey("late");
        Map<String, Object> requestContext = request.getContext();

        assertThatThrownBy(() -> requestContext.put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void runRequestToBuilder_shouldCopyAllRuntimeOverrides() {
        // Given
        RuntimeExtension extension = new RuntimeExtension() {};
        ResourceFactory resourceFactory = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                return null;
            }
        };
        IdGenerator idGenerator = () -> UUID.fromString("00000000-0000-7000-8000-000000000001");
        NestedRunContext nestedRunContext = new NestedRunContext(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "parent", "station");
        PipelineCallStack pipelineCallStack = PipelineCallStack.withMaxDepth(4);
        CancellationToken cancellationToken = new CancellationToken();
        RunRequest request = RunRequest.builder()
                .input("input")
                .context(Map.of("key", "value"))
                .resourceFactory(resourceFactory)
                .withIdGenerator(idGenerator)
                .nestedRunContext(nestedRunContext)
                .pipelineCallStack(pipelineCallStack)
                .cancellationToken(cancellationToken)
                .with(extension)
                .build();

        // When
        RunRequest copy = request.toBuilder().input("copy-input").build();

        // Then
        assertThat(copy.getInput()).isEqualTo("copy-input");
        assertThat(copy.getContext()).containsEntry("key", "value");
        assertThat(copy.getResourceFactory()).isSameAs(resourceFactory);
        assertThat(copy.getIdGenerator()).isSameAs(idGenerator);
        assertThat(copy.getNestedRunContext()).isSameAs(nestedRunContext);
        assertThat(copy.getPipelineCallStack()).isSameAs(pipelineCallStack);
        assertThat(copy.getCancellationToken()).isSameAs(cancellationToken);
        assertThat(copy.getExtensions()).containsExactly(extension);
    }

    @Test
    void runRequestBuilder_shouldRejectNullExtension() {
        // Given
        RunRequest.Builder builder = RunRequest.builder();

        // When / Then
        assertThatThrownBy(() -> builder.with(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void assemblyLine_shouldDefensivelyCopyAndExposeReadOnlyDefaultContext() {
        // Given
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("initial", "value");

        // When
        AssemblyLine<Object, Object> pipeline = AssemblyLine.<Object, Object>builder("pipeline").context(context)
                .build();
        context.put("late", "mutation");

        // Then
        assertThat(pipeline.getDefaultContext()).containsEntry("initial", "value").doesNotContainKey("late");
        Map<String, Object> defaultContext = pipeline.getDefaultContext();

        assertThatThrownBy(() -> defaultContext.put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void assemblyLineBuilderCopy_shouldNotShareMutableContextWithSourceBuilder() {
        // Given
        AssemblyLine.Builder<String, String> source = AssemblyLine.<String, String>builder("pipeline")
                .putContext("initial", "value");

        // When
        AssemblyLine.Builder<String, Integer> typed = source.then(new TestStation());
        source.putContext("late", "mutation");

        // Then
        assertThat(typed.build().getDefaultContext()).containsEntry("initial", "value").doesNotContainKey("late");
    }

    private static final class TestStation extends AbstractStation<String, Integer> {
        private TestStation() {
            super("test-station", StationKind.OTHER);
        }
    }
}
