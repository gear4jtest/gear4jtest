package io.test.gear4jtest.xml.parser;

import java.io.IOException;

import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XmlPipelineParserTest {

    private final XmlPipelineParser parser = new XmlPipelineParser();

    private static java.io.InputStream resource(String name) throws IOException {
        var input = XmlPipelineParserTest.class.getResourceAsStream(name);
        if (input == null) {
            throw new IOException("Missing test resource: " + name);
        }
        return input;
    }

    @Test
    void should_parse_operations_dependencies_and_configuration() throws IOException {
        // Given / When
        var definition = parser.parse(resource("/samples/assembly-line-iterator.xml"));

        // Then
        assertThat(definition.id()).isEqualTo("test_iterator");
        assertThat(definition.inputType()).isEqualTo("java.lang.String");
        assertThat(definition.operations()).hasSize(4);
        assertThat(definition.operations().get(0)).isInstanceOf(ProcessingOperation.class);
        assertThat(definition.operations().get(3)).isInstanceOf(IteratorOperation.class);
        assertThat(definition.dependencies()).singleElement().satisfies(dependency -> {
            assertThat(dependency.name()).isEqualTo("modelsService");
            assertThat(dependency.type()).isEqualTo("com.myorg.services.ModelsService");
        });
        assertThat(definition.configuration().eventHandling().eventOnParameterChanged()).isTrue();
    }

    @Test
    void should_parse_container_branches() throws IOException {
        // Given / When
        var definition = parser.parse(resource("/samples/assembly-line-parallel-container.xml"));

        // Then
        assertThat(definition.operations().get(1)).isInstanceOfSatisfying(ContainerOperation.class, container -> {
            assertThat(container.id()).isEqualTo("parallelContainer");
            assertThat(container.parallel()).isTrue();
            assertThat(container.threadPoolSize()).isEqualTo(2);
            assertThat(container.subLines()).hasSize(2);
        });
    }

    @Test
    void should_parse_signal_operation() throws IOException {
        // Given / When
        var definition = parser.parse(resource("/samples/assembly-line-signal.xml"));

        // Then
        assertThat(definition.operations()).hasSize(2);
        assertThat(definition.operations().get(1)).isInstanceOfSatisfying(SignalOperation.class, signal -> {
            assertThat(signal.id()).isEqualTo("stop_when_a");
            assertThat(signal.type()).isEqualTo("STOP");
            assertThat(signal.inputType()).isEqualTo("java.lang.String");
            assertThat(signal.condition().expression()).isEqualTo("input.endsWith(\"a\")");
        });
    }
}
