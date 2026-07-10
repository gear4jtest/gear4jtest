package io.github.gear4jtest.core.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.assemblyline.AssemblyLineCallStack;
import io.github.gear4jtest.core.api.assemblyline.NestedRunContext;
import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunRequestBuilderTest {
    @Test
    void builder_shouldPreserveTheCompileTimeInputType() {
        // Given / When
        RunRequest<String> request = RunRequest.builder().input("typed-input").build();

        // Then
        String input = request.getInput();
        assertThat(input).isEqualTo("typed-input");
    }

    @Test
    void builder_shouldExposeDefaultsAndCopyNullContextToEmptyMap() {
        // When
        RunRequest request = RunRequest.builder().context(null).build();

        // Then
        assertThat(request.getInput()).isNull();
        assertThat(request.getContext()).isEmpty();
        assertThat(request.getExtensions()).isEmpty();
        assertThat(request.getResourceFactory()).isNull();
        assertThat(request.getIdGenerator()).isNull();
        assertThat(request.getNestedRunContext()).isNull();
        assertThat(request.getAssemblyLineCallStack()).isNull();
        assertThat(request.getCancellationToken()).isNull();
    }

    @Test
    void toBuilder_shouldDefensivelyCopyExtensionsAndContext() {
        // Given
        Map<String, Object> sourceContext = new LinkedHashMap<>();
        sourceContext.put("tenant", "acme");
        RuntimeExtension extension = new RuntimeExtension() {};
        ResourceFactory resourceFactory = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                return null;
            }
        };
        IdGenerator idGenerator = () -> UUID.fromString("00000000-0000-7000-8000-000000000123");
        NestedRunContext nested = new NestedRunContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "parent", "station");
        AssemblyLineCallStack callStack = AssemblyLineCallStack.withMaxDepth(5);
        CancellationToken token = new CancellationToken();
        RunRequest request = RunRequest.builder()
                .input("input")
                .context(sourceContext)
                .resourceFactory(resourceFactory)
                .withIdGenerator(idGenerator)
                .nestedRunContext(nested)
                .assemblyLineCallStack(callStack)
                .cancellationToken(token)
                .with(extension)
                .build();

        // When
        RunRequest copy = request.toBuilder().context(Map.of("copied", true)).build();
        sourceContext.put("late", "mutation");

        // Then
        assertThat(copy.getInput()).isEqualTo("input");
        assertThat(copy.getContext()).containsOnly(Map.entry("copied", true));
        assertThat(copy.getResourceFactory()).isSameAs(resourceFactory);
        assertThat(copy.getIdGenerator()).isSameAs(idGenerator);
        assertThat(copy.getNestedRunContext()).isSameAs(nested);
        assertThat(copy.getAssemblyLineCallStack()).isSameAs(callStack);
        assertThat(copy.getCancellationToken()).isSameAs(token);
        assertThat(copy.getExtensions()).containsExactly(extension);
        assertThatThrownBy(() -> copy.getExtensions().add(new RuntimeExtension() {}))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toIndependentBuilder_shouldCopyReusableValuesWithoutSharingCancellationOrCallStack() {
        // Given
        RuntimeExtension extension = new RuntimeExtension() {};
        IdGenerator idGenerator = () -> UUID.fromString("00000000-0000-7000-8000-000000000124");
        NestedRunContext nested = new NestedRunContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "parent", "station");
        AssemblyLineCallStack callStack = AssemblyLineCallStack.withMaxDepth(5);
        CancellationToken token = new CancellationToken();
        RunRequest request = RunRequest.builder()
                .input("input")
                .context(Map.of("tenant", "acme"))
                .withIdGenerator(idGenerator)
                .nestedRunContext(nested)
                .assemblyLineCallStack(callStack)
                .cancellationToken(token)
                .with(extension)
                .build();

        // When
        RunRequest independent = request.toIndependentBuilder().input("copy-input").build();

        // Then
        assertThat(independent.getInput()).isEqualTo("copy-input");
        assertThat(independent.getContext()).containsEntry("tenant", "acme");
        assertThat(independent.getIdGenerator()).isSameAs(idGenerator);
        assertThat(independent.getNestedRunContext()).isNull();
        assertThat(independent.getExtensions()).containsExactly(extension);
        assertThat(independent.getAssemblyLineCallStack()).isNull();
        assertThat(independent.getCancellationToken()).isNull();
    }

}
