package io.github.gear4jtest.xml.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.SignalOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void should_reject_missing_subline_id() throws IOException {
        // Given / When / Then
        assertThatThrownBy(() -> parser.parse(resource("/samples/bad-missing-subline-id.xml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required attribute 'id' on <subLine>");
    }

    @Test
    void should_reject_doctype_declarations() throws IOException {
        // Given / When / Then
        assertThatThrownBy(() -> parser.parse(resource("/samples/bad-doctype.xml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse Gear4J XML pipeline");
    }

    @Test
    void should_reject_external_entity_declarations() {
        // Given
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE assemblyLine [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <assemblyLine xmlns="http://github.com/gear4jtest/core/model"
                              id="xxe"
                              inputType="java.lang.String"
                              outputType="java.lang.String">
                  <operations/>
                </assemblyLine>
                """;

        // When / Then
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse Gear4J XML pipeline");
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
