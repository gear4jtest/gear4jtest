//package io.github.gear4jtest.core.model;
//
//import java.util.List;
//import java.util.UUID;
//import java.util.function.Supplier;
//
//import io.github.gear4jtest.core.event.EventManager;
//import io.github.gear4jtest.core.execution.AssemblyRunManager;
//import io.github.gear4jtest.core.spi.factory.ResourceFactory;
//import io.github.gear4jtest.core.api.context.DefaultStationExecutionContext;
//import io.github.gear4jtest.core.api.context.ExecutionContext;
//import io.github.gear4jtest.core.api.behavior.Operator;
//import io.github.gear4jtest.core.api.station.WorkStation;
//import io.github.gear4jtest.core.api.context.StationContextUtils;
//import io.github.gear4jtest.core.api.context.StationExecutionContext;
//import io.github.gear4jtest.core.api.station.StationKind;
//import io.github.gear4jtest.core.engine.support.WorkerParamsInjector;
//import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.persistence.StationLogRecord;
//import org.junit.jupiter.api.Test;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.Mockito.*;
//
//class WorkStationTest {
//
//    static class UpperCaseOperator implements Operator<String, String> {
//        WorkerParamsInjector.Parameter<String> prefix = WorkerParamsInjector.Parameter.<String>newBuilder().defaultValue("").build();
//
//        @Override
//        public String transform(String input,
//                                ExecutionContext context,
//                                StationExecutionContext operationExecution) {
//            String value = input != null ? input.toUpperCase() : "";
//            return prefix.getValue() + value;
//        }
//    }
//
//    @Test
//    void setUp_shouldRegisterTransformerAndParametersInCapabilities() {
//        EventManager eventManager = mock(EventManager.class);
//        AssemblyRunManager executionManager = mock(AssemblyRunManager.class);
//        ResourceFactory resourceFactory = mock(ResourceFactory.class);
//
//        UpperCaseOperator transformer = new UpperCaseOperator();
//        when(resourceFactory.getResource(UpperCaseOperator.class)).thenReturn(transformer);
//
//        ExecutionContext globalContext =
//                new ExecutionContext(UUID.randomUUID(), "pipe", eventManager, resourceFactory, executionManager, null);
//
//        StationLogTrace record =
//                StationLogTrace.start("exec", "op", null);
//        DefaultStationExecutionContext opCtx =
//                new DefaultStationExecutionContext("op", StationKind.PROCESSING, globalContext, record);
//
//        WorkStation<String, String> def = new WorkStation<>();
//        def.type = (Class) UpperCaseOperator.class;
//        def.parameters = List.of(new WorkerParamsInjector.InterpretationContextParameterModel<>(
//                (WorkStation.ParamRetriever<UpperCaseOperator, String>) op -> op.prefix,
//                ctx -> ">> ")
//        );
//        def.processors = List.of(new WorkerParamsInjector());
//
//        // Appel de setUp, comme le ferait AbstractOperationDefinition.run(...)
//        def.setUp("hello", globalContext, opCtx);
//
//        // Le transformer doit être dans les capabilities
//        assertThat(StationContextUtils.<String, String>getTypedTransformer(opCtx))
//                .containsSame(transformer);
//
//        // Les paramètres doivent être présents
//        var paramsOpt = StationContextUtils.getProcessingParameters(opCtx);
//        assertThat(paramsOpt).isPresent();
//        assertThat(paramsOpt.get().hasParameters()).isTrue();
//        assertThat(paramsOpt.get().getParameters()).hasSize(1);
//    }
//
//    @Test
//    void builder_shouldAddOperationParamsInjectorOnlyOnceAndStoreParameters() {
//        WorkStation.Builder<String, String, UpperCaseOperator> builder =
//                new WorkStation.Builder<>();
//
//        WorkStation.ParamRetriever<UpperCaseOperator, String> retriever =
//                op -> op.prefix;
//
//        Supplier<String> supplier = () -> "prefix-";
//
//        WorkStation<String, String> def = builder
//                .type(UpperCaseOperator.class)
//                .id("op-builder")
//                .parameter(retriever, "value1")
//                .parameter(retriever, supplier)
//                .build();
//
//        // On doit avoir 2 ParameterModel dans la définition
//        assertThat(def.getParameters()).hasSize(2);
//
//        // Et un seul OperationParamsInjector dans les processors
//        long injectorCount = def.getProcessors().stream()
//                .filter(p -> p instanceof WorkerParamsInjector)
//                .count();
//        assertThat(injectorCount).isEqualTo(1);
//    }
//
//    @Test
//    void doExecute_shouldDelegateToTypedTransformer() {
//        ExecutionContext globalContext =
//                new ExecutionContext(UUID.randomUUID(), "pipe", null, null, null, null);
//        StationLogTrace record =
//                StationLogTrace.start("exec", "op", null);
//        DefaultStationExecutionContext opCtx =
//                new DefaultStationExecutionContext("op", StationKind.PROCESSING, globalContext, record);
//
//        UpperCaseOperator transformer = new UpperCaseOperator();
//        transformer.prefix = WorkerParamsInjector.Parameter.<String>newBuilder().defaultValue("**").build();
//
//        // On simule le travail de setUp : on pose directement le transformer dans les capabilities
//        opCtx.addCapability(Operator.class, transformer);
//
//        WorkStation<String, String> def = new WorkStation<>();
//
//        String result = def.doExecute("abc", globalContext, opCtx);
//
//        assertThat(result).isEqualTo("**ABC");
//    }
//
//    @Test
//    void doExecute_shouldThrowIfNoTransformerPresentInContext() {
//        ExecutionContext globalContext =
//                new ExecutionContext(UUID.randomUUID(), "pipe", null, null, null, null);
//        StationLogTrace record =
//                StationLogTrace.start("exec", "op", null);
//        DefaultStationExecutionContext opCtx =
//                new DefaultStationExecutionContext("op", StationKind.PROCESSING, globalContext, record);
//
//        WorkStation<String, String> def = new WorkStation<>();
//
//        assertThatThrownBy(() -> def.doExecute("abc", globalContext, opCtx))
//                .isInstanceOf(IllegalStateException.class)
//                .hasMessageContaining("No transformer present");
//    }
//}
