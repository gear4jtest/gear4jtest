package io.github.gear4jtest.xml.generator;

import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Parameters;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SubLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationTypeResolverDeepCoverageTest {
    @Test
    void resolve_shouldReuseCachedSignatureWhenSameOperationAppearsMoreThanOnce() {
        // Given
        ProcessingOperation shared = processing("shared", StringToIntegerOperator.class, null);
        XmlPipelineDefinition definition = definition("java.lang.String", shared, shared);

        // When
        Map<Operation, OperationSignature> signatures = resolve(definition);

        // Then
        assertThat(signatures).hasSize(1);
        assertThat(signatures.get(shared).inputType()).isEqualTo(JavaTypeName.STRING);
        assertThat(signatures.get(shared).outputType()).isEqualTo(JavaTypeName.raw("java.lang.Integer"));
    }

    @Test
    void processing_shouldPreferExplicitInputOverOperatorInputAndKeepOperatorOutputWithoutFallbackOutput() {
        // Given
        ProcessingOperation operation = processing("processing", StringToIntegerOperator.class, "java.lang.Long");
        XmlPipelineDefinition definition = definition("java.lang.String", operation);

        // When
        OperationSignature signature = resolve(definition).get(operation);

        // Then
        assertThat(signature.inputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signature.outputType()).isEqualTo(JavaTypeName.raw("java.lang.Integer"));
    }

    @Test
    void iterator_shouldFallbackToExplicitOrObjectOutputForUnknownAccumulatorVariants() {
        // Given
        ProcessingOperation child = processing("child", StringToIntegerOperator.class, null);
        IteratorOperation explicitOutput = new IteratorOperation("it-explicit", "java.lang.String", "java.lang.Double",
                null, child, "CUSTOM", null);
        IteratorOperation objectOutput = new IteratorOperation("it-object", "java.lang.String", null,
                null, child, "CUSTOM", null);

        // When
        Map<Operation, OperationSignature> explicitSignatures = resolve(definition("java.lang.String", explicitOutput));
        Map<Operation, OperationSignature> objectSignatures = resolve(definition("java.lang.String", objectOutput));

        // Then
        assertThat(explicitSignatures.get(explicitOutput).outputType()).isEqualTo(JavaTypeName.raw("java.lang.Double"));
        assertThat(objectSignatures.get(objectOutput).outputType()).isEqualTo(JavaTypeName.OBJECT);
    }

    @Test
    void containerIfElseAndSignal_shouldUseExplicitInputAndOutputWhenPresent() {
        // Given
        ProcessingOperation containerChild = processing("container-child", StringToIntegerOperator.class, null);
        ContainerOperation container = new ContainerOperation("container", "java.lang.String", "java.lang.Integer",
                true, 2, List.of(new SubLine("main", null, containerChild)), "return args[0]");
        ProcessingOperation ifChild = processing("if-child", StringToIntegerOperator.class, null);
        IfElseOperation ifElse = new IfElseOperation("ifelse", "java.lang.String", null,
                List.of(new ConditionalOperation("when", new Condition("true", null), ifChild)), null);
        SignalOperation signal = new SignalOperation("signal", "IGNORE", "java.lang.Long", null);
        XmlPipelineDefinition definition = definition("java.lang.Object", container, ifElse, signal);

        // When
        Map<Operation, OperationSignature> signatures = resolve(definition);

        // Then
        assertThat(signatures.get(container).inputType()).isEqualTo(JavaTypeName.STRING);
        assertThat(signatures.get(container).outputType()).isEqualTo(JavaTypeName.raw("java.lang.Integer"));
        assertThat(signatures.get(containerChild).inputType()).isEqualTo(JavaTypeName.STRING);
        assertThat(signatures.get(ifElse).inputType()).isEqualTo(JavaTypeName.STRING);
        assertThat(signatures.get(ifElse).outputType()).isEqualTo(JavaTypeName.STRING);
        assertThat(signatures.get(ifChild).inputType()).isEqualTo(JavaTypeName.STRING);
        assertThat(signatures.get(signal).inputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signatures.get(signal).outputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
    }

    @Test
    void operatorResolution_shouldFallbackUnboundOutputTypeVariableToObjectAndKeepCurrentInput() {
        // Given
        ProcessingOperation operation = processing("generic", UnboundGenericOperator.class, null);

        // When
        OperationSignature signature = resolve(definition("java.lang.String", operation)).get(operation);

        // Then
        assertThat(signature.inputType()).isEqualTo(JavaTypeName.STRING);
        assertThat(signature.outputType()).isEqualTo(JavaTypeName.OBJECT);
    }

    private static Map<Operation, OperationSignature> resolve(XmlPipelineDefinition definition) {
        return new OperationTypeResolver(OperationTypeResolverDeepCoverageTest.class.getClassLoader())
                .resolve(definition);
    }

    private static XmlPipelineDefinition definition(String inputType, Operation... operations) {
        return new XmlPipelineDefinition("pipeline", inputType, null, List.of(operations), null, List.of());
    }

    private static ProcessingOperation processing(String id, Class<?> operatorType, String inputType) {
        return new ProcessingOperation(id, operatorType.getName(), inputType, new Parameters(List.of()), List.of(),
                List.of(), null);
    }

    static final class StringToIntegerOperator implements Operator<String, Integer> {
        @Override
        public Integer transform(String input, StationExecutionContext operationExecution) {
            return input.length();
        }
    }

    static final class UnboundGenericOperator<T> implements Operator<T, T> {
        @Override
        public T transform(T input, StationExecutionContext operationExecution) {
            return input;
        }
    }
}
