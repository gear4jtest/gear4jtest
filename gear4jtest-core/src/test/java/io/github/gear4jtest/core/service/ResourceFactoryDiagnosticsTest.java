package io.github.gear4jtest.core.service;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.util.AssemblyLines;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static io.github.gear4jtest.core.api.util.Stations.processingOperation;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceFactoryDiagnosticsTest {
    @Test
    void missingOperatorResource_shouldFailWithExplicitDiagnostic() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("missing-resource")
                .then(processingOperation("missing-step", DiagnosticOperator.class).build())
                .build();
        ResourceFactory resourceFactory = new MissingResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);

        // When
        ExecutionResult<String> result = engine.execute(assemblyLine,
                                                        RunRequest.builder().input("input")
                                                                .resourceFactory(resourceFactory).build());

        // Then
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError()).hasMessageContaining("ResourceFactory returned null")
                .hasMessageContaining(DiagnosticOperator.class.getName())
                .hasMessageContaining("missing-step");
    }

    @Test
    void incompatibleOperatorResource_shouldFailWithExplicitDiagnostic() {
        // Given
        var assemblyLine = AssemblyLines.<String>createAssemblyLine("bad-resource")
                .then(processingOperation("bad-step", DiagnosticOperator.class).build())
                .build();
        ResourceFactory resourceFactory = new IncompatibleResourceFactory();
        var engine = CoreRuntimeTestSupport.newEngine(resourceFactory);

        // When
        ExecutionResult<String> result = engine.execute(assemblyLine,
                                                        RunRequest.builder().input("input")
                                                                .resourceFactory(resourceFactory).build());

        // Then
        assertThat(result.isFailed()).isTrue();
        assertThat(result.getError()).hasMessageContaining("ResourceFactory returned incompatible resource")
                .hasMessageContaining(String.class.getName())
                .hasMessageContaining(DiagnosticOperator.class.getName())
                .hasMessageContaining("bad-step");
    }

    static final class DiagnosticOperator implements Operator<String, String> {
        @Override
        public String transform(String input, StationExecutionContext operationExecution) {
            return input;
        }
    }

    private static final class MissingResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            return null;
        }
    }

    private static final class IncompatibleResourceFactory implements ResourceFactory {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getResource(Class<T> clazz) {
            return (T) "not-an-operator";
        }
    }
}
