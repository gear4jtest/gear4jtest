package io.github.gear4jtest.core.model.refactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.PipelineExecutionManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

class ProcessingOperationDefinitionTest {

    static class UpperCaseTransformer implements Transformer<String, String> {
        OperationParamsInjector.Parameter<String> prefix = OperationParamsInjector.Parameter.ofDefault("");

        @Override
        public String transform(String input,
                                ExecutionContext context,
                                OperationExecutionContext operationExecution) {
            String value = input != null ? input.toUpperCase() : "";
            return prefix.getValue() + value;
        }
    }

    @Test
    void setUp_shouldRegisterTransformerAndParametersInCapabilities() {
        EventManager eventManager = mock(EventManager.class);
        PipelineExecutionManager executionManager = mock(PipelineExecutionManager.class);
        ResourceFactory resourceFactory = mock(ResourceFactory.class);

        UpperCaseTransformer transformer = new UpperCaseTransformer();
        when(resourceFactory.getResource(UpperCaseTransformer.class)).thenReturn(transformer);

        ExecutionContext globalContext =
                new ExecutionContext("pipe", eventManager, resourceFactory, executionManager);

        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec", "op", null);
        DefaultOperationExecutionContext opCtx =
                new DefaultOperationExecutionContext("op", OperationKind.PROCESSING, globalContext, record);

        ProcessingOperationDefinition<String, String> def = new ProcessingOperationDefinition<>();
        def.type = (Class) UpperCaseTransformer.class;
        def.parameters = List.of(
                new OperationParamsInjector.ValueParameterModel<>(
                        (ProcessingOperationDefinition.ParamRetriever<UpperCaseTransformer, String>) op -> op.prefix,
                        ">> "
                )
        );
        def.processors = List.of(new OperationParamsInjector());

        // Appel de setUp, comme le ferait AbstractOperationDefinition.run(...)
        def.setUp("hello", globalContext, opCtx);

        // Le transformer doit être dans les capabilities
        assertThat(OperationContextUtils.<String, String>getTypedTransformer(opCtx))
                .containsSame(transformer);

        // Les paramètres doivent être présents
        var paramsOpt = OperationContextUtils.getProcessingParameters(opCtx);
        assertThat(paramsOpt).isPresent();
        assertThat(paramsOpt.get().hasParameters()).isTrue();
        assertThat(paramsOpt.get().getParameters()).hasSize(1);
    }

    @Test
    void builder_shouldAddOperationParamsInjectorOnlyOnceAndStoreParameters() {
        ProcessingOperationDefinition.Builder<String, String, UpperCaseTransformer> builder =
                new ProcessingOperationDefinition.Builder<>();

        ProcessingOperationDefinition.ParamRetriever<UpperCaseTransformer, String> retriever =
                op -> op.prefix;

        Supplier<String> supplier = () -> "prefix-";

        ProcessingOperationDefinition<String, String> def = builder
                .type(UpperCaseTransformer.class)
                .id("op-builder")
                .parameter(retriever, "value1")
                .parameter(retriever, supplier)
                .build();

        // On doit avoir 2 ParameterModel dans la définition
        assertThat(def.getParameters()).hasSize(2);

        // Et un seul OperationParamsInjector dans les processors
        long injectorCount = def.getProcessors().stream()
                .filter(p -> p instanceof OperationParamsInjector)
                .count();
        assertThat(injectorCount).isEqualTo(1);
    }

    @Test
    void doExecute_shouldDelegateToTypedTransformer() {
        ExecutionContext globalContext =
                new ExecutionContext("pipe", null, null, null);
        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec", "op", null);
        DefaultOperationExecutionContext opCtx =
                new DefaultOperationExecutionContext("op", OperationKind.PROCESSING, globalContext, record);

        UpperCaseTransformer transformer = new UpperCaseTransformer();
        transformer.prefix = OperationParamsInjector.Parameter.ofDefault("**");

        // On simule le travail de setUp : on pose directement le transformer dans les capabilities
        opCtx.addCapability(Transformer.class, transformer);

        ProcessingOperationDefinition<String, String> def = new ProcessingOperationDefinition<>();

        String result = def.doExecute("abc", globalContext, opCtx);

        assertThat(result).isEqualTo("**ABC");
    }

    @Test
    void doExecute_shouldThrowIfNoTransformerPresentInContext() {
        ExecutionContext globalContext =
                new ExecutionContext("pipe", null, null, null);
        OperationExecutionRecord record =
                OperationExecutionRecord.start("exec", "op", null);
        DefaultOperationExecutionContext opCtx =
                new DefaultOperationExecutionContext("op", OperationKind.PROCESSING, globalContext, record);

        ProcessingOperationDefinition<String, String> def = new ProcessingOperationDefinition<>();

        assertThatThrownBy(() -> def.doExecute("abc", globalContext, opCtx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transformer present");
    }
}
