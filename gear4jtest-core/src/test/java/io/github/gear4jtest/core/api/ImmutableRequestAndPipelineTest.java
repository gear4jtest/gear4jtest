package io.github.gear4jtest.core.api;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.StationKind;
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
        assertThatThrownBy(() -> request.getContext().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
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
        assertThatThrownBy(() -> pipeline.getDefaultContext().put("other", "value"))
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
