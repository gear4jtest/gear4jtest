package io.github.gear4jtest.xml.generator;

import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ConditionalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IfElseOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Parameters;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Transformer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationTypeResolverAdditionalTest {
    @Test
    void processing_shouldUseCurrentInputForObjectOperatorAndFallbackOutputType() {
        // Given
        ProcessingOperation operation = new ProcessingOperation("operation", ObjectToStringOperator.class.getName(),
                null, params(), List.of(), List.of(), new Transformer("fallback", null, "java.lang.Integer"));
        XmlAssemblyLineDefinition definition = definition("java.lang.Long", operation);

        // When
        OperationSignature signature = resolve(definition).get(operation);

        // Then
        assertThat(signature.inputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signature.outputType()).isEqualTo(JavaTypeName.raw("java.lang.Integer"));
    }

    @Test
    void iterator_shouldResolveCollectorAccumulatorAndExplicitOutputVariants() {
        assertIteratorOutput(new IteratorOperation("iterator", null, null, null, child("child-list"), null,
                "java.util.stream.Collectors.toList()"), "java.util.List<java.lang.String>");
        assertIteratorOutput(new IteratorOperation("iterator", null, null, null, child("child-set"), null,
                "Collectors.toSet()"), "java.util.Set<java.lang.String>");
        assertIteratorOutput(new IteratorOperation("iterator", null, null, null, child("child-acc-list"), "LIST",
                null), "java.util.List<java.lang.String>");
        assertIteratorOutput(new IteratorOperation("iterator", null, null, null, child("child-acc-set"), "SET",
                null), "java.util.Set<java.lang.String>");
        assertIteratorOutput(new IteratorOperation("iterator", null, "java.lang.Long", null, child("child-explicit"),
                "CUSTOM", null), "java.lang.Long");
        assertIteratorOutput(new IteratorOperation("iterator", "java.lang.Iterable<java.lang.Integer>", null, null,
                child("child-direct"), null, null), "java.lang.String");
    }

    @Test
    void containerIfElseAndSignal_shouldPropagateEffectiveInputTypes() {
        // Given
        ProcessingOperation containerChild = child("container-child");
        ContainerOperation container = new ContainerOperation("container", null, null, false, null,
                List.of(new SubLine("line", null, containerChild)), null);
        ProcessingOperation whenChild = child("when-child");
        ProcessingOperation elseChild = child("else-child");
        IfElseOperation ifElse = new IfElseOperation("choice", null, "java.lang.Double",
                List.of(new ConditionalOperation("when", new Condition("true", null), whenChild)), elseChild);
        SignalOperation signal = new SignalOperation("stop", "STOP", null, null);
        XmlAssemblyLineDefinition definition = definition("java.lang.Long", container, ifElse, signal);

        // When
        Map<Operation, OperationSignature> signatures = resolve(definition);

        // Then
        assertThat(signatures.get(container).inputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signatures.get(container).outputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signatures.get(containerChild).inputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signatures.get(ifElse).outputType()).isEqualTo(JavaTypeName.raw("java.lang.Double"));
        assertThat(signatures.get(whenChild).inputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signatures.get(elseChild).inputType()).isEqualTo(JavaTypeName.raw("java.lang.Long"));
        assertThat(signatures.get(signal).inputType()).isEqualTo(JavaTypeName.raw("java.lang.Double"));
        assertThat(signatures.get(signal).outputType()).isEqualTo(JavaTypeName.raw("java.lang.Double"));
    }

    @Test
    void processing_shouldResolveInheritedParameterizedOperatorTypes() {
        // Given
        ProcessingOperation operation = new ProcessingOperation("operation", ItemOperator.class.getName(), null,
                params(), List.of(), List.of(), null);
        XmlAssemblyLineDefinition definition = definition("java.lang.Object", operation);

        // When
        OperationSignature signature = resolve(definition).get(operation);

        // Then
        assertThat(signature.inputType().canonical())
                .isEqualTo("java.util.List<" + Item.class.getCanonicalName() + ">");
        assertThat(signature.outputType().canonical())
                .isEqualTo("java.util.Map<java.lang.String, " + Item.class.getCanonicalName() + ">");
    }

    @Test
    void resolve_shouldRejectUnsupportedOperationAndUnresolvableOperatorClasses() {
        // Given
        ProcessingOperation missingClass = new ProcessingOperation("missing", "not.found.Operator", null, params(),
                List.of(), List.of(), null);
        ProcessingOperation rawOperator = new ProcessingOperation("raw", RawOperator.class.getName(), null, params(),
                List.of(), List.of(), null);

        // When / Then
        assertThatThrownBy(() -> resolve(definition("java.lang.String", missingClass)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to load operator class");
        assertThatThrownBy(() -> resolve(definition("java.lang.String", rawOperator)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to resolve Operator<IN, OUT> generic parameters");
    }

    @Test
    void resolvedParameterizedType_shouldBeDefensivelyImmutableAndUseArrayContent() throws Exception {
        // Given
        ParameterizedType first = resolvedParameterizedType(Map.class, new Type[] { String.class, Integer.class });
        ParameterizedType second = resolvedParameterizedType(Map.class, new Type[] { String.class, Integer.class });
        Type[] exposedArguments = first.getActualTypeArguments();

        // When
        exposedArguments[0] = Long.class;

        // Then
        assertThat(first.getActualTypeArguments()).containsExactly(String.class, Integer.class);
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isNotEqualTo(resolvedParameterizedType(Map.class, new Type[] { String.class, Long.class }));
        assertThat(first.toString()).contains("rawType=interface java.util.Map")
                .contains("actualTypeArguments=[class java.lang.String, class java.lang.Integer]");
    }

    private static Map<Operation, OperationSignature> resolve(XmlAssemblyLineDefinition definition) {
        return new OperationTypeResolver(OperationTypeResolverAdditionalTest.class.getClassLoader())
                .resolve(definition);
    }

    private static void assertIteratorOutput(IteratorOperation operation, String expectedOutputType) {
        XmlAssemblyLineDefinition definition = definition("java.util.List<java.lang.Integer>", operation);
        assertThat(resolve(definition).get(operation).outputType().canonical()).isEqualTo(expectedOutputType);
    }

    private static XmlAssemblyLineDefinition definition(String inputType, Operation... operations) {
        return new XmlAssemblyLineDefinition("pipeline", inputType, null, List.of(operations), null, List.of());
    }

    private static ProcessingOperation child(String id) {
        return new ProcessingOperation(id, ObjectToStringOperator.class.getName(), null, params(), List.of(), List.of(),
                null);
    }

    private static Parameters params() {
        return new Parameters(List.of());
    }

    private static ParameterizedType resolvedParameterizedType(Class<?> rawType, Type[] arguments) throws Exception {
        Class<?> type = Class.forName(OperationTypeResolver.class.getName() + "$ResolvedParameterizedType");
        Constructor<?> constructor = type.getDeclaredConstructor(Class.class, Type[].class, Type.class);
        constructor.setAccessible(true);
        return (ParameterizedType) constructor.newInstance(rawType, arguments, null);
    }

    static class BaseOperator<T> implements Operator<List<T>, Map<String, T>> {
        @Override
        public Map<String, T> transform(List<T> input, StationExecutionContext operationExecution) {
            return Map.of();
        }
    }

    static final class ItemOperator extends BaseOperator<Item> {
    }

    static final class ObjectToStringOperator implements Operator<Object, String> {
        @Override
        public String transform(Object input, StationExecutionContext operationExecution) {
            return String.valueOf(input);
        }
    }

    @SuppressWarnings("rawtypes")
    static final class RawOperator implements Operator {
        @Override
        public Object transform(Object input, StationExecutionContext operationExecution) {
            return input;
        }
    }

    static final class Item {
    }
}
