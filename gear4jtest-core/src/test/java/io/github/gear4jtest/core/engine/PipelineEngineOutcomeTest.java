package io.github.gear4jtest.core.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionOutcome;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.ElementModelBuilders;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineEngineOutcomeTest {
    @Test
    void skippedProcessingOperationWithFallback_shouldExposeSkippedOutcomeAndFallbackOutput() {
        // Given
        AssemblyLine<String, String> pipeline = ElementModelBuilders.<String>createAssemblyLine("skip-root")
                .then(ElementModelBuilders.processingOperation("skipped", EchoOperator.class)
                        .skipIf((input, ctx) -> true)
                        .transformer(new FallbackOperator())
                        .build())
                .build();
        PipelineEngine engine = PipelineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();

        // When
        var result = engine.execute(pipeline, RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getOutcome()).as("a conditionally skipped operation remains visible as skipped")
                .isEqualTo(ExecutionOutcome.SKIPPED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isSkipped()).isTrue();
        assertThat(result.getResult()).as("the fallback transformer still supplies the output used by the flow")
                .isEqualTo("fallback-input");
        assertThat(result.getExecution().getStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    }

    @Test
    void skippedUnaryOperation_shouldExposeSkippedOutcomeAndPassInputThrough() {
        // Given
        AssemblyLine<String, String> pipeline = ElementModelBuilders.<String>createAssemblyLine("skip-root-unary")
                .then(ElementModelBuilders.unaryProcessingOperation("skipped", EchoOperator.class)
                        .skipIf((input, ctx) -> true)
                        .build())
                .build();
        PipelineEngine engine = PipelineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(null))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();

        // When
        var result = engine.execute(pipeline, RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getOutcome()).as("a skipped unary operation is skipped, even if its input is carried forward")
                .isEqualTo(ExecutionOutcome.SKIPPED);
        assertThat(result.isSkipped()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResult()).isEqualTo("input");
        assertThat(result.getExecution().getStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    }

    @Test
    void skippedIntermediateOperationWithFallback_shouldRemainSkippedButFeedNextStation() {
        // Given
        AssemblyLine<String, String> pipeline = ElementModelBuilders.<String>createAssemblyLine("skip-then-continue")
                .then(ElementModelBuilders.processingOperation("skipped", EchoOperator.class)
                        .skipIf((input, ctx) -> true)
                        .transformer(new FallbackOperator())
                        .build())
                .then(ElementModelBuilders.unaryProcessingOperation("next", AppendOperator.class)
                        .build())
                .build();
        RecordingStationLifecycleExtension stationRecorder = new RecordingStationLifecycleExtension();
        PipelineEngine engine = PipelineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(List.of(stationRecorder)))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();

        // When
        var result = engine.execute(pipeline, RunRequest.builder().input("input").build());

        // Then
        assertThat(result.getOutcome()).as("the root sequence succeeds because the flow had a usable output")
                .isEqualTo(ExecutionOutcome.SUCCEEDED);
        assertThat(result.getResult()).as("the skipped child fallback output must be passed to downstream stations")
                .isEqualTo("fallback-input-next");
        assertThat(stationRecorder.completedStatuses()).containsEntry("skipped", StationLogStatus.SKIPPED)
                .containsEntry("next", StationLogStatus.SUCCEEDED);
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    private static final class RecordingStationLifecycleExtension implements StationLifecycleExtension {
        private final Map<String, StationLogStatus> completedStatuses = new LinkedHashMap<>();

        @Override
        public void onStationCompleted(ExecutionContext runCtx,
                                       StationExecutionContext stationCtx,
                                       StationLogRecord snapshot) {
            completedStatuses.put(snapshot.operationId(), snapshot.status());
        }

        private Map<String, StationLogStatus> completedStatuses() {
            return completedStatuses;
        }
    }

    public static final class EchoOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }

    public static final class AppendOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input + "-next";
        }
    }

    private static final class FallbackOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return "fallback-" + input;
        }
    }
}
