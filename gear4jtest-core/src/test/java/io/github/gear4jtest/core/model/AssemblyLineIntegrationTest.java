//package io.github.gear4jtest.core.model;
//
//import java.util.Map;
//import java.util.Optional;
//
//import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
//import io.github.gear4jtest.core.factory.ResourceFactory;
//import io.github.gear4jtest.core.model.AssemblyLine;
//import io.github.gear4jtest.core.model.ExecutionContext;
//import io.github.gear4jtest.core.model.ExecutionResult;
//import io.github.gear4jtest.core.model.Operator;
//import io.github.gear4jtest.core.model.WorkStation;
//import io.github.gear4jtest.core.model.StationExecutionContext;
//import io.github.gear4jtest.core.persistence.InMemoryAssemblyRunRepository;
//import io.github.gear4jtest.core.persistence.StationLog;
//import io.github.gear4jtest.core.persistence.AssemblyRun;
//import org.junit.jupiter.api.Test;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//class AssemblyLineIntegrationTest {
//
//    // ----------- Transformeurs concrets pour le test -----------
//
//    static class ToUpperCase implements Operator<String, String> {
//        @Override
//        public String transform(String input,
//                                ExecutionContext context,
//                                StationExecutionContext operationExecution) {
//            return input == null ? null : input.toUpperCase();
//        }
//    }
//
//    static class AppendSuffix implements Operator<String, String> {
//
//        private final String suffix;
//
//        AppendSuffix(String suffix) {
//            this.suffix = suffix;
//        }
//
//        @Override
//        public String transform(String input,
//                                ExecutionContext context,
//                                StationExecutionContext operationExecution) {
//            return input == null ? suffix : input + suffix;
//        }
//    }
//
//    @Test
//    void execute_shouldRunAllOperations_andPersistExecutionRecords_inMemory() {
//        // ResourceFactory simple pour nos deux transformeurs
//        ResourceFactory factory = new TestResourceFactory();
//
//        // ----------- Définition des opérations -----------
//
//        WorkStation.Builder<String, String, ToUpperCase> upperBuilder =
//                new WorkStation.Builder<>();
//
//        WorkStation<String, String> upperOp = upperBuilder
//                .type(ToUpperCase.class)
//                .id("upper")
//                .build();
//
//        WorkStation.Builder<String, String, AppendSuffix> suffixBuilder =
//                new WorkStation.Builder<>();
//
//        WorkStation<String, String> suffixOp = suffixBuilder
//                .type(AppendSuffix.class)
//                .id("suffix")
//                .build();
//
//        // ----------- Définition de la pipeline / assembly line -----------
//
//        AssemblyLine.Builder<String, String> lineBuilder =
//                AssemblyLine.builder("test-line");
//
//        AssemblyLine<String, String> line = lineBuilder
//                .then(upperOp)
//                .then(suffixOp)
//                .build();
//
//        // ----------- Exécution -----------
//
//        ExecutionResult<String> result =
//                line.execute("hello", Map.of(), factory, new InMemoryExecutionManager());
//
//        assertThat(result.isSuccess()).isTrue();
//        assertThat(result.getResult()).isEqualTo("HELLO!");
//
//        // ----------- Vérification de la persistance en mémoire -----------
//
//        Optional<AssemblyRun> execOpt =
//                InMemoryAssemblyRunRepository.INSTANCE.findById(result.getExecution().getId());
//
//        assertThat(execOpt).isPresent();
//
//        AssemblyRun exec = execOpt.get();
//        assertThat(exec.getPipelineId()).isEqualTo("test-line");
//        assertThat(exec.getOperations()).hasSize(2);
//
//        StationLog first = exec.getOperations().get(0);
//        StationLog second = exec.getOperations().get(1);
//
//        assertThat(first.getOperationId()).isEqualTo("upper");
//        assertThat(second.getOperationId()).isEqualTo("suffix");
//
//        assertThat(first.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
//        assertThat(second.getStatus()).isEqualTo(StationLog.Status.SUCCEEDED);
//
//        assertThat(second.getOutput(String.class)).isEqualTo("HELLO!");
//    }
//
//    public static class TestResourceFactory implements ResourceFactory {
//        @Override
//        public <T> T getResource(Class<T> clazz) {
//            if (clazz.equals(ToUpperCase.class)) {
//                return (T) new ToUpperCase();
//            }
//            if (clazz.equals(AppendSuffix.class)) {
//                return (T) new AppendSuffix("!");
//            }
//            throw new IllegalArgumentException("Unexpected resource: " + clazz);
//        }
//    }
//}
