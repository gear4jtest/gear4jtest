package io.github.gear4jtest.core.api.station;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.behavior.SignalType;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StationBuildersDeepCoverageTest {
    @Test
    void workStationBuilder_shouldCopyConfiguredProcessorsErrorsFallbackMetadataAndParameters() {
        // Given
        Processor processor = new NoOpProcessor();
        BaseError.SafeError<String> safeError = new BaseError.SafeError.Builder<String>(SignalType.STOP,
                RuntimeException.class).build();
        IdentityOperator fallback = new IdentityOperator();
        Function<WorkerParamsInjector.InterpretationContext<String>, String> resolver = ctx -> ctx.getItem()
                + "-resolved";

        // When
        WorkStation<String, String> station = new WorkStation.Builder<String, String, ParamOperator>()
                .id("work")
                .type(ParamOperator.class)
                .reuseOperatorInstanceWithinRun()
                .addProcessor(processor)
                .parameter(ParamOperator::fixedParameter, "fixed")
                .parameter(ParamOperator::suppliedParameter, (Supplier<String>) () -> "supplied")
                .parameter(ParamOperator::resolvedParameter, resolver)
                .onError(safeError)
                .fallback(fallback)
                .metadata(String.class, "metadata")
                .build();

        // Then
        assertThat(station.getId()).isEqualTo("work");
        assertThat(station.getType()).isEqualTo(ParamOperator.class);
        assertThat(station.isReuseOperatorInstanceWithinRun()).isTrue();
        assertThat(station.getProcessors()).hasAtLeastOneElementOfType(WorkerParamsInjector.class).contains(processor);
        assertThat(station.getParameters()).hasSize(3);
        assertThat(station.getOnErrors()).containsExactly(safeError);
        assertThat(station.getFallbackOperator()).isSameAs(fallback);
        assertThat(station.getMetadata().get(String.class)).contains("metadata");
    }

    @Test
    void unsafeAndSafeBuilders_shouldPreserveChainedConfigurationUntilBuild() {
        // Given
        BaseError.UnSafeError<String> unsafe = new BaseError.UnSafeError.Builder<String>(SignalType.IGNORE,
                IllegalArgumentException.class).build();
        BaseError.SafeError<String> safe = new BaseError.SafeError.Builder<String>(SignalType.FATAL,
                IllegalStateException.class).build();

        // When
        WorkStation<String, String> station = new WorkStation.Builder<String, String, IdentityOperator>()
                .id("unsafe")
                .type(IdentityOperator.class)
                .onError(unsafe)
                .onError(safe)
                .skipIf((input, ctx) -> true)
                .skipIfPost((input, ctx) -> false)
                .transformer(new IdentityOperator())
                .build();

        // Then
        assertThat(station.getOnErrors()).containsExactly(unsafe, safe);
        assertThat(station.getSkippers()).hasSize(2);
        assertThat(station.getFallbackOperator()).isNotNull();
    }

    @Test
    void unaryWorkStationBuilder_shouldMarkStationAsUnaryAndCopyConfiguration() {
        // When
        UnaryWorkStation<String> station = new UnaryWorkStation.Builder<String, IdentityOperator>()
                .id("unary")
                .type(IdentityOperator.class)
                .parameter(IdentityOperator::ignoredParameter, "value")
                .skipIf((input, ctx) -> false)
                .fallback(new IdentityOperator())
                .build();

        // Then
        assertThat(station.getId()).isEqualTo("unary");
        assertThat(station.getUnary()).isTrue();
        assertThat(station.getParameters()).hasSize(1);
        assertThat(station.getSkippers()).hasSize(1);
        assertThat(station.getFallbackOperator()).isNotNull();
    }

    @Test
    void containerBranchBuilder_shouldPreserveConditionsAndSiblingConditions() {
        // Given
        WorkStation<String, String> station = new WorkStation.Builder<String, String, IdentityOperator>()
                .type(IdentityOperator.class)
                .id("branch")
                .build();
        AtomicInteger conditionCalls = new AtomicInteger();
        AtomicInteger siblingCalls = new AtomicInteger();

        // When
        ContainerBaseStation.Branch<String> branch = new ContainerBaseStation.Branch.Builder<String>()
                .withId("branch-id")
                .withOperation(station)
                .withCondition((input, ctx) -> {
                    conditionCalls.incrementAndGet();
                    return true;
                })
                .withSiblingCondition((input, ctx, outcomes) -> {
                    siblingCalls.incrementAndGet();
                    return true;
                })
                .build();

        // Then
        assertThat(branch.getId()).isEqualTo("branch-id");
        assertThat(branch.getId()).isEqualTo("branch-id");
        assertThat(branch.getStation()).isSameAs(station);
        assertThat(branch.getCondition().test("input", null)).isTrue();
        assertThat(branch.getSiblingCondition().test("input", null, null)).isTrue();
        assertThat(conditionCalls.get()).isEqualTo(1);
        assertThat(siblingCalls.get()).isEqualTo(1);
    }

    private static final class NoOpProcessor implements Processor {
        @Override
        public <I> void beforeExecution(I input, StationExecutionContext ctx) {
            // No-op processor used only to verify builder copying.
        }

        @Override
        public void afterExecution(Object result, StationExecutionContext context) {
            // No-op processor used only to verify builder copying.
        }
    }

    static final class IdentityOperator implements Operator<String, String> {
        private final WorkerParamsInjector.Parameter<String> ignored = WorkerParamsInjector.Parameter
                .<String>newBuilder()
                .build();

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }

        WorkerParamsInjector.Parameter<String> ignoredParameter() {
            return ignored;
        }
    }

    static final class ParamOperator implements Operator<String, String> {
        private final WorkerParamsInjector.Parameter<String> fixed = WorkerParamsInjector.Parameter
                .<String>newBuilder()
                .build();
        private final WorkerParamsInjector.Parameter<String> supplied = WorkerParamsInjector.Parameter
                .<String>newBuilder()
                .build();
        private final WorkerParamsInjector.Parameter<String> resolved = WorkerParamsInjector.Parameter
                .<String>newBuilder()
                .build();

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return List.of(fixed.getValue(), supplied.getValue(), resolved.getValue()).toString();
        }

        WorkerParamsInjector.Parameter<String> fixedParameter() {
            return fixed;
        }

        WorkerParamsInjector.Parameter<String> suppliedParameter() {
            return supplied;
        }

        WorkerParamsInjector.Parameter<String> resolvedParameter() {
            return resolved;
        }
    }
}
