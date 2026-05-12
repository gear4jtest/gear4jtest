package io.test.gear4jtest.xml.validator;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyLineValidatorTest {

    private final AssemblyLineValidator validator = new AssemblyLineValidator();

    private static byte[] resource(String name) throws IOException {
        try (var input = AssemblyLineValidatorTest.class.getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + name);
            }
            return input.readAllBytes();
        }
    }

    @Test
    void should_validate_current_xml_contract() throws IOException {
        // Given
        byte[] xml = resource("/samples/assembly-line-iterator.xml");

        // When / Then
        assertThatCode(() -> validator.validate(xml)).doesNotThrowAnyException();
    }

    @Test
    void should_reject_invalid_xml_contract() throws IOException {
        // Given
        byte[] xml = resource("/samples/bad-assembly-line.xml");

        // When / Then
        assertThatThrownBy(() -> validator.validate(xml)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Gear4J XML pipeline definition");
    }

    @Test
    void should_reject_ifelse_without_else_operation() throws IOException {
        // Given
        byte[] xml = resource("/samples/bad-missing-else-operation.xml");

        // When / Then
        assertThatThrownBy(() -> validator.validate(xml)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Gear4J XML pipeline definition");
    }
}
