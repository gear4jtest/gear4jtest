package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationBuilderCoverageTest {
    @Test
    void unaryContainerBuilder_shouldBuildParallelContainerWithOneLineAndFunction() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            WorkStation<String, String> branchStation = new WorkStation.Builder<String, String, IdentityOperator>()
                    .type(IdentityOperator.class)
                    .id("branch-op")
                    .build();

            UnaryContainerStation<String> station = new UnaryContainerStation.Builder<String>()
                    .parallel(executor)
                    .awaitTimeout(Duration.ofSeconds(2))
                    .withOneLine("branch-1", branchStation, (input, ctx) -> true, value -> value + "!")
                    .build();

            assertThat(station.isParallel()).isTrue();
            assertThat(station.getExecutorService()).isSameAs(executor);
            assertThat(station.getAwaitTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(station.getPipelines()).hasSize(1);
            assertThat(station.getPipelines().get(0).getEffectiveId()).isEqualTo("branch-1");
            assertThat(station.getPipelines().get(0).getStation()).isSameAs(branchStation);
            assertThat(station.getPipelines().get(0).getCondition()).isNotNull();
            assertThat(station.getFunc().apply("gear")).isEqualTo("gear!");
            assertThatThrownBy(() -> station.getPipelines().add(station.getPipelines().get(0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void containerBuilders_shouldRejectDuplicatedOrBlankBranchIds() {
        WorkStation<String, String> branchStation = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("branch-op")
                .build();

        assertThatThrownBy(() -> new ContainerBaseStation.Builder<String, Void>()
                .withSubLine("same", branchStation)
                .withSubLine("same", branchStation)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Container contains duplicated branch id 'same'");

        assertThatThrownBy(() -> new UnaryContainerStation.Builder<String>()
                .withOneLine(" ", branchStation)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("branch id is required");
    }

    @Test
    void unaryWorkStationBuilder_shouldExposeUnarySpecificFallbackAndErrorFlow() {
        BaseError.UnSafeError<String> unsafeError = new BaseError.UnSafeError.Builder<String>(SignalType.STOP,
                RuntimeException.class).build();
        Operator<String, String> fallback = (input, ctx) -> "fallback-" + input;

        UnaryWorkStation<String> station = new UnaryWorkStation.Builder<String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("unary")
                .onError(unsafeError)
                .transformer(fallback)
                .skipIf((input, ctx) -> true)
                .skipIfPost((input, ctx) -> false)
                .build();

        assertThat(station.getId()).isEqualTo("unary");
        assertThat(station.getType()).isEqualTo(IdentityOperator.class);
        assertThat(station.getUnary()).isTrue();
        assertThat(station.getOnErrors()).containsExactly(unsafeError);
        assertThat(station.getFallbackOperator()).isSameAs(fallback);
        assertThat(station.getSkippers()).hasSize(2);
    }

    @Test
    void workStationBuilder_shouldAddParameterInjectorOnlyOnceAndPreserveMetadata() {
        Processor processor = new NoOpProcessor();

        WorkStation<String, String> station = new WorkStation.Builder<String, String, ParameterizedOperator>()
                .type(ParameterizedOperator.class)
                .id("work")
                .addProcessor(processor)
                .parameter(ParameterizedOperator::parameter, "one")
                .parameter(ParameterizedOperator::parameter, (Supplier<String>) () -> "two")
                .metadata(String.class, "meta")
                .reuseOperatorInstanceWithinRun()
                .build();

        assertThat(station.getProcessors()).hasSize(2);
        assertThat(station.getProcessors()).contains(processor);
        assertThat(station.getParameters()).hasSize(2);
        assertThat(station.isReuseOperatorInstanceWithinRun()).isTrue();
        assertThat(station.getMetadata().get(String.class)).contains("meta");
    }

    @Test
    void containerBaseBuilder_shouldBuildTwoLineContainerWithSiblingCondition() {
        WorkStation<String, String> branchStation = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("branch-op")
                .build();

        ContainerBaseStation<String, String> station = new ContainerBaseStation.Builder<String, String>()
                .withSubLine("a", branchStation)
                .withSubLine("b", branchStation, (input, ctx, siblings) -> true)
                .returns((left, right) -> left + right);

        assertThat(station.getPipelines()).extracting(ContainerBaseStation.Branch::getEffectiveId)
                .containsExactly("a", "b");
        assertThat(station.getPipelines().get(1).getSiblingCondition()).isNotNull();
        assertThat(station.getFunc().apply("A", "B")).isEqualTo("AB");
    }

    static class IdentityOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext ctx) {
            return input;
        }
    }

    static class ParameterizedOperator implements Operator<String, String> {
        private final io.github.gear4jtest.core.engine.support.WorkerParamsInjector.Parameter<String> parameter = io.github.gear4jtest.core.engine.support.WorkerParamsInjector.Parameter
                .<String>newBuilder().build();

        @Override
        public String transform(String input, StationExecutionContext ctx) {
            return input;
        }

        io.github.gear4jtest.core.engine.support.WorkerParamsInjector.Parameter<String> parameter() {
            return parameter;
        }
    }

    private static final class NoOpProcessor implements Processor {
        @Override
        public <I> void beforeExecution(I input, StationExecutionContext ctx) {
        }

        @Override
        public void afterExecution(Object result, StationExecutionContext context) {
        }
    }
}
