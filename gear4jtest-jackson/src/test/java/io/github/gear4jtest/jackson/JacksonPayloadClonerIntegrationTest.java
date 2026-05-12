package io.github.gear4jtest.jackson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.station.ContainerBaseStation;
import io.github.gear4jtest.core.api.station.UnaryWorkStation;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.engine.PipelineEngine;
import io.github.gear4jtest.core.engine.RuntimeExtensionResolver;
import io.github.gear4jtest.core.engine.runner.RunnerChainFactory;
import io.github.gear4jtest.core.engine.strategy.StrategyRegistry;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonPayloadClonerIntegrationTest {

    private static PipelineEngine newEngine(Object payloadCloner) {
        PipelineEngine.Builder builder = PipelineEngine.builder().resourceFactory(resourceFactory())
                .runnerChainFactory(new RunnerChainFactory(StrategyRegistry.defaultRegistry()))
                .extensionResolver(new RuntimeExtensionResolver(List.of()))
                .executionContextRegistry(new ExecutionContextRegistry());

        if (payloadCloner instanceof io.github.gear4jtest.core.api.context.PayloadCloner cloner) {
            builder.payloadCloner(cloner);
        }

        return builder.build();
    }

    private static ResourceFactory resourceFactory() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                if (type == AddBranchOneOperator.class) {
                    return type.cast(new AddBranchOneOperator());
                }
                if (type == AddBranchTwoOperator.class) {
                    return type.cast(new AddBranchTwoOperator());
                }
                throw new IllegalArgumentException("Unsupported resource type: " + type.getName());
            }
        };
    }

    private static AssemblyLine<MutablePayload, MutablePayload> parallelContainerPipeline(ExecutorService executor) {
        UnaryWorkStation<MutablePayload> branchOne = ElementModelBuilders
                .unaryProcessingOperation("branch-one", AddBranchOneOperator.class).build();

        UnaryWorkStation<MutablePayload> branchTwo = ElementModelBuilders
                .unaryProcessingOperation("branch-two", AddBranchTwoOperator.class).build();

        ContainerBaseStation<MutablePayload, MutablePayload> container = ElementModelBuilders
                .container(MutablePayload.class, executor).withSubLine("id1", branchOne).withSubLine("id2", branchTwo)
                .returns((MutablePayload left, MutablePayload right) -> MutablePayload.merge(left, right));

        return AssemblyLine.<MutablePayload, MutablePayload>builder("parallel-container").then(container).build();
    }

    @Test
    void should_fail_parallel_container_with_default_strict_cloner_on_mutable_payload() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Given
            PipelineEngine engine = newEngine(null);
            AssemblyLine<MutablePayload, MutablePayload> pipeline = parallelContainerPipeline(executor);
            MutablePayload input = MutablePayload.seed("seed");

            // When
            ExecutionResult<MutablePayload> result = engine.execute(pipeline,
                                                                    RunRequest.builder().input(input).build());

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getError()).isNotNull();
            assertThat(result.getError().getMessage()).contains(MutablePayload.class.getName());
            assertThat(input.getValues()).containsExactly("seed");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void should_isolate_each_parallel_branch_when_jackson_payload_cloner_is_configured() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Given
            PipelineEngine engine = newEngine(JacksonPayloadCloners.defaultMapper());
            AssemblyLine<MutablePayload, MutablePayload> pipeline = parallelContainerPipeline(executor);
            MutablePayload input = MutablePayload.seed("seed");

            // When
            ExecutionResult<MutablePayload> result = engine.execute(pipeline,
                                                                    RunRequest.builder().input(input).build());

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getResult()).isNotNull();
            assertThat(result.getResult().getValues()).containsExactly("seed", "branch-1", "seed", "branch-2");
            assertThat(input.getValues()).containsExactly("seed");
        } finally {
            executor.shutdownNow();
        }
    }

    public static final class AddBranchOneOperator implements Operator<MutablePayload, MutablePayload> {
        @Override
        public MutablePayload transform(MutablePayload input,
                                        io.github.gear4jtest.core.api.context.StationExecutionContext operationExecution) {
            input.getValues().add("branch-1");
            return input;
        }
    }

    public static final class AddBranchTwoOperator implements Operator<MutablePayload, MutablePayload> {
        @Override
        public MutablePayload transform(MutablePayload input,
                                        io.github.gear4jtest.core.api.context.StationExecutionContext operationExecution) {
            input.getValues().add("branch-2");
            return input;
        }
    }

    public static final class MutablePayload {
        private List<String> values = new ArrayList<>();

        public MutablePayload() {
        }

        public static MutablePayload seed(String value) {
            MutablePayload payload = new MutablePayload();
            payload.getValues().add(value);
            return payload;
        }

        public static MutablePayload merge(MutablePayload left, MutablePayload right) {
            MutablePayload merged = new MutablePayload();
            merged.getValues().addAll(left.getValues());
            merged.getValues().addAll(right.getValues());
            return merged;
        }

        public List<String> getValues() {
            return values;
        }

        public void setValues(List<String> values) {
            this.values = values;
        }
    }
}
