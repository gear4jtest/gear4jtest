package io.github.gear4jtest.xml.generator;

import java.util.List;
import java.util.Map;

import io.github.gear4jtest.core.api.behavior.Operator;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationTypeResolverTest {
    @Test
    void iterator_shouldResolveChildOperationWithCollectionItemType() {
        // Given
        ProcessingOperation child = new ProcessingOperation("child", ObjectToStringOperator.class.getName(), null,
                new XmlAssemblyLineDefinition.Parameters(List.of()), List.of(), List.of(), null);
        IteratorOperation iterator = new IteratorOperation("iterator", null, null, null, child, null, null);
        XmlAssemblyLineDefinition definition = new XmlAssemblyLineDefinition("pipeline",
                "java.util.List<" + Item.class.getName() + ">", null, List.of(iterator), null, List.of());

        // When
        Map<XmlAssemblyLineDefinition.Operation, OperationSignature> signatures = new OperationTypeResolver(getClass()
                .getClassLoader()).resolve(definition);

        // Then
        assertThat(signatures.get(child).inputType()).isEqualTo(JavaTypeName.raw(Item.class.getName()));
        assertThat(signatures.get(iterator).outputType()).isEqualTo(JavaTypeName.STRING);
    }

    static final class ObjectToStringOperator implements Operator<Object, String> {
        @Override
        public String transform(Object input, StationExecutionContext operationExecution) {
            return String.valueOf(input);
        }
    }

    static final class Item {
    }
}
