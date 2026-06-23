package io.github.gear4jtest.core.api.station;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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

class StationBuilderTest {
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
            assertThat(station.getUnary()).isTrue();
            assertThat(station.getExecutorService()).isSameAs(executor);
            assertThat(station.getAwaitTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(station.getAssemblyLines()).hasSize(1);
            assertThat(station.getAssemblyLines().get(0).getId()).isEqualTo("branch-1");
            assertThat(station.getAssemblyLines().get(0).getStation()).isSameAs(branchStation);
            assertThat(station.getAssemblyLines().get(0).getCondition()).isNotNull();
            assertThat(station.getResultsFunc().apply(ContainerResults.of(Map.of("branch-1", "gear"), List.of("gear"))))
                    .isEqualTo("gear!");
            var branches = station.getAssemblyLines();
            var firstBranch = branches.get(0);

            assertThatThrownBy(() -> branches.add(firstBranch))
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

        var duplicatedBranchBuilder = new ContainerBaseStation.Builder<String, Void>()
                .withBranch(ContainerBranch.of("same", branchStation))
                .withBranch(ContainerBranch.of("same", branchStation));

        assertThatThrownBy(duplicatedBranchBuilder::build)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Container contains duplicated branch id 'same'");

        assertThatThrownBy(() -> new UnaryContainerStation.Builder<String>().withOneLine(" ", branchStation))
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
    void containerBaseBuilder_shouldBuildNamedTypedContainerWithMoreThanTwoBranches() {
        WorkStation<String, String> branchStation = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("branch-op")
                .build();
        ContainerBranch<String, String> first = ContainerBranch.of("first", branchStation);
        ContainerBranch<String, String> second = ContainerBranch.of("second", branchStation);
        ContainerBranch<String, String> third = ContainerBranch.of("third", branchStation);

        ContainerBaseStation<String, String> station = new ContainerBaseStation.Builder<String, String>()
                .withBranch(first)
                .withBranch(second, (input, ctx) -> true)
                .withBranch(third, (input, ctx, siblings) -> true)
                .returns(results -> results.get(first) + results.get(second) + results.get(third));

        assertThat(station.getAssemblyLines()).extracting(ContainerBaseStation.Branch::getId)
                .containsExactly("first", "second", "third");
        assertThat(station.getAssemblyLines().get(1).getCondition()).isNotNull();
        assertThat(station.getAssemblyLines().get(2).getSiblingCondition()).isNotNull();
        assertThat(station.getResultsFunc().apply(ContainerResults.of(Map.of("first", "A", "second", "B",
                                                                             "third", "C"),
                                                                      List.of("A", "B", "C"))))
                .isEqualTo("ABC");
    }

    @Test
    void containerResults_shouldValidateNamedTypedAccess() {
        WorkStation<String, String> textStation = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("text-op")
                .build();
        ContainerBranch<String, String> text = ContainerBranch.of("text", textStation);
        ContainerResults results = ContainerResults.of(Map.of("text", "value", "number", 42), List.of("value", 42));

        assertThat(results.get(text)).isEqualTo("value");
        assertThat(results.get("number", Integer.class)).isEqualTo(42);
        assertThat(results.orderedOutputs()).containsExactly("value", 42);
        assertThatThrownBy(() -> results.get("number", String.class))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("Container branch 'number' produced java.lang.Integer");
        assertThatThrownBy(() -> results.get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown container branch result 'missing'");
    }

    @Test
    void containerBaseBuilder_shouldBuildTwoLineContainerWithSiblingCondition() {
        WorkStation<String, String> branchStation = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("branch-op")
                .build();
        ContainerBranch<String, String> first = ContainerBranch.of("a", branchStation);
        ContainerBranch<String, String> second = ContainerBranch.of("b", branchStation);

        ContainerBaseStation<String, String> station = new ContainerBaseStation.Builder<String, String>()
                .withBranch(first)
                .withBranch(second, (input, ctx, siblings) -> true)
                .returns(results -> results.get(first) + results.get(second));

        assertThat(station.getUnary()).isFalse();
        assertThat(station.getAssemblyLines()).extracting(ContainerBaseStation.Branch::getId)
                .containsExactly("a", "b");
        assertThat(station.getAssemblyLines().get(1).getSiblingCondition()).isNotNull();
        assertThat(station.getResultsFunc().apply(ContainerResults.of(Map.of("a", "A", "b", "B"),
                                                                      List.of("A", "B"))))
                .isEqualTo("AB");
    }

    @Test
    void signalStationBuilder_shouldBuildUnaryStation() {
        SignalStation<String> station = new SignalStation.Builder<String>()
                .id("fatal")
                .type(SignalType.FATAL)
                .build();

        assertThat(station.getSignalType()).isEqualTo(SignalType.FATAL);
        assertThat(station.getUnary()).isTrue();
        assertThat(station.getProcessors()).isEmpty();
        assertThat(station.getOnErrors()).isEmpty();
    }

    @Test
    void signalStationBuilder_shouldRejectIgnoreSignal() {
        assertThatThrownBy(() -> new SignalStation.Builder<String>().type(SignalType.IGNORE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SignalStation does not support IGNORE; use STOP or FATAL");
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
            // No-op processor used only to exercise builder coverage.
        }

        @Override
        public void afterExecution(Object result, StationExecutionContext context) {
            // No-op processor used only to exercise builder coverage.
        }
    }
}
