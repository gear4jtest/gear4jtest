package io.github.gear4jtest.core.api.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.AssemblyLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineDefinitionTest {
    @Test
    void builder_shouldCaptureIterablePipelineAccumulatorAndCollector() {
        AssemblyLine<Integer, String> nested = AssemblyLine.<Integer, String>builder("nested").build();
        PipelineDefinition.ListAccumulator accumulator = new PipelineDefinition.ListAccumulator();

        PipelineDefinition<String, List<String>> definition = new PipelineDefinition.Builder<String, Integer>()
                .iterableFunction(input -> List.of(input.length()))
                .pipeline(nested)
                .accumulator(accumulator)
                .collector(Collectors.toList())
                .build();

        assertThat(definition.getFunc().apply("gear").iterator().next()).isEqualTo(4);
        assertThat(definition.getAssemblyLine()).isSameAs(nested);
        assertThat(definition.getAccumulator()).isSameAs(accumulator);
        assertThat(definition.getCollector()).isNotNull();
    }

    @Test
    void accumulators_shouldExposeExpectedCollectionSuppliers() {
        assertThat(new PipelineDefinition.ListAccumulator().getCollectionSupplier().getSupplier().get())
                .isInstanceOf(ArrayList.class);
        assertThat(new PipelineDefinition.SetAccumulator().getCollectionSupplier().getSupplier().get())
                .isInstanceOf(HashSet.class);
    }
}
