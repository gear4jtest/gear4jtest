package io.github.gear4jtest.core.api.station;

import java.util.List;
import java.util.stream.Collectors;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedBuilderIsolationTest {
    @Test
    void workStationTypeTransition_shouldNotMutateSourceBuilder() {
        // Given
        WorkStation.Builder<String, String, IdentityOperator> source = new WorkStation.Builder<>();
        source.id("source");

        // When
        WorkStation.Builder<String, String, IdentityOperator> typed = source.type(IdentityOperator.class);

        // Then
        assertThatThrownBy(source::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operator type is required");
        assertThat(typed.build().getType()).isEqualTo(IdentityOperator.class);
    }

    @Test
    void unaryWorkStationTypeTransition_shouldNotMutateSourceBuilder() {
        // Given
        UnaryWorkStation.Builder<String, IdentityOperator> source = new UnaryWorkStation.Builder<>();
        source.id("source");

        // When
        UnaryWorkStation.Builder<String, IdentityOperator> typed = source.type(IdentityOperator.class);

        // Then
        assertThatThrownBy(source::build)
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operator type is required");
        assertThat(typed.build().getType()).isEqualTo(IdentityOperator.class);
    }

    @Test
    void sequenceTransition_shouldNotMutateOrShareStepsWithSourceBuilder() {
        // Given
        SequenceStation.Builder<String, String> source = SequenceStation.Builder.create("sequence");
        StringToIntegerStation station = new StringToIntegerStation();

        // When
        SequenceStation.Builder<String, Integer> typed = source.next(station);

        // Then
        assertThat(source.build().getSteps()).isEmpty();
        assertThat(typed.build().getSteps()).containsExactly(station);
    }

    @Test
    void iteratorIterableTransition_shouldNotMutateSourceBuilder() {
        // Given
        IteratorStation.Builder<List<String>, List<String>> source = new IteratorStation.Builder<>("iterator");

        // When
        IteratorStation.Builder<List<String>, String> typed = source.iterableFunction(values -> values);

        // Then
        assertThat(source.build().getFunc()).isNull();
        assertThat(typed.build().getFunc()).isNotNull();
    }

    @Test
    void iteratorSequenceTransition_shouldNotMutateSourceBuilder() {
        // Given
        IteratorStation.Builder<String, String> source = new IteratorStation.Builder<>("iterator");
        SequenceStation<String, Integer> sequence = SequenceStation.Builder.<String>create("sequence")
                .next(new StringToIntegerStation())
                .build();

        // When
        IteratorStation.Builder<String, Integer> typed = source.sequence(sequence);

        // Then
        assertThat(source.build().getChain()).isNull();
        assertThat(typed.build().getChain()).isSameAs(sequence);
    }

    @Test
    void iteratorCollectorTransition_shouldNotMutateSourceBuilder() {
        // Given
        IteratorStation.Builder<String, String> source = new IteratorStation.Builder<>("iterator");

        // When
        IteratorStation.Builder<String, List<String>> typed = source.collector(Collectors.toList());

        // Then
        assertThat(source.build().getCollector()).isNull();
        assertThat(typed.build().getCollector()).isNotNull();
    }

    @Test
    void stationBuilders_shouldRejectBlankIdAndMissingOperatorType() {
        assertThatThrownBy(() -> new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("station id is required");
        assertThatThrownBy(() -> new WorkStation.Builder<String, String, IdentityOperator>()
                .id("station")
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("operator type is required");
    }

    private static final class IdentityOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }

    private static final class StringToIntegerStation extends AbstractStation<String, Integer> {
        private StringToIntegerStation() {
            super("length", StationKind.CUSTOM, null, null, null, false, null, null);
        }
    }
}
