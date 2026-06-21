package io.github.gear4jtest.core.model;

import java.util.function.Supplier;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.WorkStation;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkStationTest {
    @Test
    void builder_shouldAddWorkerParamsInjectorOnlyOnceAndStoreParameters() {
        // Given
        WorkStation.Builder<String, String, PrefixOperator> builder = new WorkStation.Builder<>();
        Supplier<String> suppliedPrefix = () -> "supplied-";

        // When
        WorkStation<String, String> station = builder.type(PrefixOperator.class).id("prefix")
                .parameter(PrefixOperator::getPrefix, "fixed-").parameter(PrefixOperator::getPrefix, suppliedPrefix)
                .build();

        // Then
        assertThat(station.getId()).isEqualTo("prefix");
        assertThat(station.getType()).isEqualTo(PrefixOperator.class);
        assertThat(station.getParameters()).hasSize(2);
        assertThat(station.getProcessors()).filteredOn(WorkerParamsInjector.class::isInstance).hasSize(1);
    }

    @Test
    void getters_shouldExposeImmutableNonNullCollections() {
        // Given / When
        WorkStation<String, String> station = new WorkStation.Builder<String, String, PrefixOperator>()
                .type(PrefixOperator.class).id("immutable").build();

        // Then
        assertThat(station.getParameters()).isEmpty();
        assertThat(station.getProcessors()).isEmpty();
        assertThat(station.getOnErrors()).isEmpty();
        var parameters = station.getParameters();
        var processors = station.getProcessors();
        Processor processor = new NoopProcessor();

        assertThatThrownBy(() -> parameters.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> processors.add(processor))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void typedBuilder_shouldNotShareMutableListsWithSourceBuilder() {
        // Given
        WorkStation.Builder<String, String, PrefixOperator> source = new WorkStation.Builder<>();
        WorkStation.Builder<String, String, PrefixOperator> typed = source.type(PrefixOperator.class).id("typed");

        // When
        source.addProcessor(new NoopProcessor());
        WorkStation<String, String> station = typed.build();

        // Then
        assertThat(station.getProcessors()).isEmpty();
    }

    @Test
    void reuseOperatorInstanceWithinRun_shouldBeDisabledByDefaultAndConfigurable() {
        // Given / When
        WorkStation<String, String> defaultStation = new WorkStation.Builder<String, String, PrefixOperator>()
                .type(PrefixOperator.class).id("default").build();
        WorkStation<String, String> reusableStation = new WorkStation.Builder<String, String, PrefixOperator>()
                .type(PrefixOperator.class).id("reusable").reuseOperatorInstanceWithinRun().build();

        // Then
        assertThat(defaultStation.isReuseOperatorInstanceWithinRun()).isFalse();
        assertThat(reusableStation.isReuseOperatorInstanceWithinRun()).isTrue();
    }

    @Test
    void metadata_shouldBeAttachedByBuilder() {
        // Given / When
        WorkStation<String, String> station = new WorkStation.Builder<String, String, PrefixOperator>()
                .type(PrefixOperator.class).id("metadata").metadata(String.class, "value").build();

        // Then
        assertThat(station.getMetadata().get(String.class)).contains("value");
    }

    private static final class NoopProcessor implements Processor {
        @Override
        public <I> void beforeExecution(I input, StationExecutionContext ctx) {
            // no-op
        }

        @Override
        public void afterExecution(Object result, StationExecutionContext context) {
            // no-op
        }
    }

    private static final class PrefixOperator implements Operator<String, String> {
        private final WorkerParamsInjector.Parameter<String> prefix = WorkerParamsInjector.Parameter
                .<String>newBuilder().defaultValue("").build();

        private WorkerParamsInjector.Parameter<String> getPrefix() {
            return prefix;
        }

        @Override
        public String transform(String input, StationExecutionContext ctx) {
            return prefix.getValue() + input;
        }
    }
}
