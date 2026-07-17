package io.github.gear4jtest.xml.validator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
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

    @ParameterizedTest
    @ValueSource(strings = {
            "/samples/assembly-line-iterator.xml",
            "/samples/assembly-line-parallel-container.xml",
            "/samples/assembly-line-container-three-branches.xml",
            "/samples/assembly-line-signal.xml"
    })
    void should_validate_current_xml_contract(String sample) throws IOException {
        // Given
        byte[] xml = resource(sample);

        // When / Then
        assertThatCode(() -> validator.validate(xml)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/samples/bad-assembly-line.xml",
            "/samples/bad-missing-subline-id.xml",
            "/samples/bad-doctype.xml",
            "/samples/bad-missing-else-operation.xml",
            "/samples/bad-ignore-signal.xml"
    })
    void should_reject_invalid_xml_contracts(String sample) throws IOException {
        // Given
        byte[] xml = resource(sample);

        // When / Then
        assertThatThrownBy(() -> validator.validate(xml)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Gear4J XML pipeline definition");
    }

    @Test
    void should_reject_oversize_byte_array_before_schema_validation() {
        AssemblyLineValidator bounded = new AssemblyLineValidator(32);
        byte[] oversized = "<assemblyLine>".repeat(4).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThatThrownBy(() -> bounded.validate(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxXmlBytes=32")
                .hasMessageNotContaining("Invalid Gear4J XML pipeline definition");
    }

    @Test
    void should_bound_input_stream_before_schema_validation() {
        AssemblyLineValidator bounded = new AssemblyLineValidator(32);
        CountingInputStream input = new CountingInputStream(new ByteArrayInputStream(new byte[1_024]));

        assertThatThrownBy(() -> bounded.validate(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Gear4J XML definition exceeds maxXmlBytes=32")
                .hasMessageNotContaining("Invalid Gear4J XML pipeline definition");
        assertThat(input.bytesRead()).isEqualTo(33);
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private int bytesRead;

        private CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                bytesRead += read;
            }
            return read;
        }

        private int bytesRead() {
            return bytesRead;
        }
    }
}
