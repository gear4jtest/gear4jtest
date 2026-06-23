package io.github.gear4jtest.xml.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.IteratorOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlAssemblyLineParserTest {
    private final XmlAssemblyLineParser parser = new XmlAssemblyLineParser();

    private static java.io.InputStream resource(String name) {
        var input = XmlAssemblyLineParserTest.class.getResourceAsStream(name);
        if (input == null) {
            throw new IllegalStateException("Missing test resource: " + name);
        }
        return input;
    }

    @Test
    void should_parse_operations_dependencies_and_configuration() {
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
    void should_parse_container_branches() {
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
    void should_reject_missing_subline_id() {
        // Given
        var xml = resource("/samples/bad-missing-subline-id.xml");

        // When / Then
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required attribute 'id' on <subLine>");
    }

    @Test
    void should_reject_doctype_declarations() {
        // Given
        var xml = resource("/samples/bad-doctype.xml");

        // When / Then
        assertThatThrownBy(() -> parser.parse(xml))
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

        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        // When / Then
        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to parse Gear4J XML pipeline");
    }

    @Test
    void should_parse_gel_condition_language() {
        // Given
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <assemblyLine xmlns="http://github.com/gear4jtest/core/model"
                              id="gel"
                              inputType="java.lang.String"
                              outputType="java.lang.String">
                  <operations>
                    <signal id="stop" type="STOP" inputType="java.lang.String">
                      <condition language="gel" expression="input == &quot;a&quot;"/>
                    </signal>
                  </operations>
                </assemblyLine>
                """;

        // When
        var definition = parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Then
        assertThat(definition.operations()).singleElement().isInstanceOfSatisfying(SignalOperation.class, signal -> {
            assertThat(signal.condition().language()).isEqualTo("gel");
            assertThat(signal.condition().isGel()).isTrue();
        });
    }

    @Test
    void should_reject_ignore_signal_station_type() {
        // Given
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <assemblyLine xmlns="http://github.com/gear4jtest/core/model"
                              id="invalid-signal"
                              inputType="java.lang.String"
                              outputType="java.lang.String">
                  <operations>
                    <signal id="ignore" type="IGNORE" inputType="java.lang.String"/>
                  </operations>
                </assemblyLine>
                """;

        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        // When / Then
        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported signal station type: IGNORE");
    }

    @Test
    void should_reject_xml_that_exceeds_configured_size_limit() {
        // Given
        XmlAssemblyLineParser boundedParser = new XmlAssemblyLineParser(120);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <assemblyLine xmlns="http://github.com/gear4jtest/core/model"
                              id="too-large"
                              inputType="java.lang.String"
                              outputType="java.lang.String">
                  <operations/>
                </assemblyLine>
                """;

        ByteArrayInputStream input = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        // When / Then
        assertThatThrownBy(() -> boundedParser.parse(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining("maxXmlBytes=120");
    }

    @Test
    void should_parse_signal_operation() {
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
