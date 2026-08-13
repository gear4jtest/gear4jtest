package io.github.gear4jtest.core.engine;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.api.util.Stations;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.spi.extension.RunLifecycleExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyLineEngineFatalErrorTest {
    @Test
    void fatalJvmError_shouldEscapeWithoutBeingNormalizedAsACompletedRun() {
        // Given
        RecordingCompletionExtension lifecycle = new RecordingCompletionExtension();
        AssemblyLineEngine engine = AssemblyLineEngine.builder()
                .resourceFactory(reflectiveResourceFactory())
                .extensionResolver(new RuntimeExtensionResolver(List.of(lifecycle)))
                .executionContextRegistry(new ExecutionContextRegistry())
                .build();

        // When / Then
        assertThatThrownBy(() -> engine.execute(pipeline(), RunRequest.builder().input("input").build()))
                .isSameAs(FatalOperator.FAILURE);
        assertThat(lifecycle.completed()).as("fatal JVM errors do not enter the recoverable completion path")
                .isFalse();
    }

    private static AssemblyLine<String, String> pipeline() {
        return AssemblyLines.<String>createAssemblyLine("fatal-boundary")
                .then(Stations.processingOperation("fatal", FatalOperator.class).build())
                .build();
    }

    private static ResourceFactory reflectiveResourceFactory() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                try {
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
    }

    public static final class FatalOperator implements Operator<String, String> {
        private static final FatalTestError FAILURE = new FatalTestError("fatal operator error");

        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            throw FAILURE;
        }
    }

    private static final class RecordingCompletionExtension implements RunLifecycleExtension {
        private final AtomicBoolean completed = new AtomicBoolean();

        @Override
        public void onRunCompleted(ExecutionContext ctx, RunTrace run) {
            completed.set(true);
        }

        private boolean completed() {
            return completed.get();
        }
    }

    private static final class FatalTestError extends Error {
        private static final long serialVersionUID = 1L;

        private FatalTestError(String message) {
            super(message);
        }
    }
}
