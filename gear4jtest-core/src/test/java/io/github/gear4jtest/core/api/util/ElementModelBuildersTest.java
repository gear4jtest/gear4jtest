package io.github.gear4jtest.core.api.util;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.pipeline.PipelineExecutionMode;
import io.github.gear4jtest.core.api.station.SignalStation;
import io.github.gear4jtest.core.api.station.WorkStation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElementModelBuildersTest {
    @Test
    void errorBuilders_shouldCreateTypedSignalErrorDefinitions() {
        assertThat(ElementModelBuilders.<String>ignore(IllegalArgumentException.class).build().getSignalType())
                .isEqualTo(SignalType.IGNORE);
        assertThat(ElementModelBuilders.<String>fatal(IllegalArgumentException.class).build().getSignalType())
                .isEqualTo(SignalType.FATAL);
        assertThat(ElementModelBuilders.<String>stop(IllegalArgumentException.class).build().getSignalType())
                .isEqualTo(SignalType.STOP);
    }

    @Test
    void stationBuilders_shouldExposeCommonStationFactories() {
        // Given
        ExecutorService executor = Executors.newSingleThreadExecutor();
        WorkStation<String, String> branch = ElementModelBuilders
                .<String, String, IdentityOperator>processingOperation("identity", IdentityOperator.class)
                .build();
        try {
            // When / Then
            assertThat(ElementModelBuilders
                    .<String, String, IdentityOperator>processingOperation("work", IdentityOperator.class)
                    .build().getId()).isEqualTo("work");
            assertThat(ElementModelBuilders
                    .<String, IdentityOperator>unaryProcessingOperation("unary", IdentityOperator.class)
                    .build().getUnary()).isTrue();
            assertThat(ElementModelBuilders.iterate("items").accumulator(ElementModelBuilders.toList()).build()
                    .getId()).isEqualTo("items");
            assertThat(ElementModelBuilders.container(String.class).withSubLine("branch", branch)
                    .returns(value -> value).getPipelines()).hasSize(1);
            assertThat(ElementModelBuilders.container(String.class, executor).withSubLine("branch", branch)
                    .returns(value -> value).getExecutorService()).isSameAs(executor);
            assertThat(ElementModelBuilders.ifElseContainer(String.class).build().getPipelines()).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void signalAndPipelineFactories_shouldBuildExpectedDefinitions() {
        // Given
        WorkStation<String, String> station = ElementModelBuilders
                .<String, String, IdentityOperator>processingOperation("identity", IdentityOperator.class)
                .build();
        AssemblyLine<String, String> child = ElementModelBuilders.<String>createAssemblyLine("child")
                .then(station)
                .build();

        // When
        SignalStation<String> fatal = ElementModelBuilders.fatalSignal(String.class).id("fatal").build();
        SignalStation<Map<String, Integer>> mapFatal = ElementModelBuilders
                .fatalSignal(new ElementModelBuilders.MapType<>(String.class, Integer.class))
                .id("map-fatal")
                .build();

        // Then
        assertThat(fatal.getSignalType()).isEqualTo(SignalType.FATAL);
        assertThat(mapFatal.getSignalType()).isEqualTo(SignalType.FATAL);
        assertThat(ElementModelBuilders.inlinePipeline("inline", child).getExecutionMode())
                .isEqualTo(PipelineExecutionMode.INLINE);
        assertThat(ElementModelBuilders.nestedPipeline("nested", child).getExecutionMode())
                .isEqualTo(PipelineExecutionMode.NESTED_RUN);
        assertThat(ElementModelBuilders.<String, String>pipelineCall("call").directTarget(child).build().getId())
                .isEqualTo("call");
        assertThat(ElementModelBuilders.chain("chain", station).build().getId()).isEqualTo("chain");
    }

    @Test
    void configurationFactories_shouldCreateMutableBuildersAndValidateNullTypeTokens() {
        assertThat(ElementModelBuilders.configuration()
                .persistence(PersistenceConfiguration.builder().storeResultObject(false).build())
                .build()
                .getPersistence()
                .isStoreResultObject()).isFalse();
        assertThat(ElementModelBuilders.persistenceConfiguration().storeResultObject(false).build()
                .isStoreResultObject()).isFalse();
        assertThat(ElementModelBuilders.eventConfiguration().build()).isNotNull();
        assertThat(ElementModelBuilders.eventHandling().build()).isNotNull();
        assertThat(ElementModelBuilders.toList()).isNotNull();
        assertThat(ElementModelBuilders.toSet()).isNotNull();
        assertThatThrownBy(() -> ElementModelBuilders.fatalSignal((Class<String>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz must not be null");
        assertThatThrownBy(() -> ElementModelBuilders.container((Class<String>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz must not be null");
        assertThatThrownBy(() -> ElementModelBuilders.ifElseContainer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz must not be null");
    }

    private static final class IdentityOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext ctx) {
            return input;
        }
    }
}
