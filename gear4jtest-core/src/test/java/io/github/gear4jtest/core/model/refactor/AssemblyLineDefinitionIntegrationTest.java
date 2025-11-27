package io.github.gear4jtest.core.model.refactor;

import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.persistence.InMemoryPipelineExecutionRepository;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineDefinitionIntegrationTest {

    // ----------- Transformeurs concrets pour le test -----------

    static class ToUpperCase implements Transformer<String, String> {
        @Override
        public String transform(String input,
                                ExecutionContext context,
                                OperationExecutionContext operationExecution) {
            return input == null ? null : input.toUpperCase();
        }
    }

    static class AppendSuffix implements Transformer<String, String> {

        private final String suffix;

        AppendSuffix(String suffix) {
            this.suffix = suffix;
        }

        @Override
        public String transform(String input,
                                ExecutionContext context,
                                OperationExecutionContext operationExecution) {
            return input == null ? suffix : input + suffix;
        }
    }

    @Test
    void execute_shouldRunAllOperations_andPersistExecutionRecords_inMemory() {
        // ResourceFactory simple pour nos deux transformeurs
        ResourceFactory factory = new TestResourceFactory();

        // ----------- Définition des opérations -----------

        ProcessingOperationDefinition.Builder<String, String, ToUpperCase> upperBuilder =
                new ProcessingOperationDefinition.Builder<>();

        ProcessingOperationDefinition<String, String> upperOp = upperBuilder
                .type(ToUpperCase.class)
                .id("upper")
                .build();

        ProcessingOperationDefinition.Builder<String, String, AppendSuffix> suffixBuilder =
                new ProcessingOperationDefinition.Builder<>();

        ProcessingOperationDefinition<String, String> suffixOp = suffixBuilder
                .type(AppendSuffix.class)
                .id("suffix")
                .build();

        // ----------- Définition de la pipeline / assembly line -----------

        AssemblyLineDefinition.Builder<String, String> lineBuilder =
                AssemblyLineDefinition.<String, String>builder()
                        .id("test-line")
                        .resourceFactory(factory);

        AssemblyLineDefinition<String, String> line = lineBuilder
                .then(upperOp)
                .then(suffixOp)
                .build();

        // ----------- Exécution -----------

        ExecutionResult<String> result =
                line.execute("hello", Map.of(), factory);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("HELLO!");

        // ----------- Vérification de la persistance en mémoire -----------

        Optional<PipelineExecution> execOpt =
                InMemoryPipelineExecutionRepository.INSTANCE.findById(result.getExecutionId());

        assertThat(execOpt).isPresent();

        PipelineExecution exec = execOpt.get();
        assertThat(exec.getPipelineId()).isEqualTo("test-line");
        assertThat(exec.getOperations()).hasSize(2);

        OperationExecutionRecord first = exec.getOperations().get(0);
        OperationExecutionRecord second = exec.getOperations().get(1);

        assertThat(first.getOperationId()).isEqualTo("upper");
        assertThat(second.getOperationId()).isEqualTo("suffix");

        assertThat(first.getStatus()).isEqualTo(OperationExecutionRecord.Status.SUCCEEDED);
        assertThat(second.getStatus()).isEqualTo(OperationExecutionRecord.Status.SUCCEEDED);

        assertThat(second.getOutput(String.class)).isEqualTo("HELLO!");
    }

    public static class TestResourceFactory implements ResourceFactory {
        @Override
        public <T> T getResource(Class<T> clazz) {
            if (clazz.equals(ToUpperCase.class)) {
                return (T) new ToUpperCase();
            }
            if (clazz.equals(AppendSuffix.class)) {
                return (T) new AppendSuffix("!");
            }
            throw new IllegalArgumentException("Unexpected resource: " + clazz);
        }
    }
}
