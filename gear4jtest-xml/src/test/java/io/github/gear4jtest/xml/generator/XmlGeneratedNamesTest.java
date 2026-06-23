package io.github.gear4jtest.xml.generator;

import java.util.List;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Parameters;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ProcessingOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XmlGeneratedNamesTest {
    @Test
    void operationMethodName_shouldNormalizeXmlIdsToStableJavaNames() {
        // Given
        ProcessingOperation operation = new ProcessingOperation("123-step-name", "com.example.Step",
                "java.lang.String", new Parameters(List.of()), List.of(), List.of(), null);

        // When
        String methodName = XmlGeneratedNames.operationMethodName(operation);

        // Then
        assertThat(methodName).isEqualTo("process_123_step_name");
    }

    @Test
    void parallelExecutorFieldName_shouldUseSameIdentifierNormalizationAsGeneratedSource() {
        // Given / When / Then
        assertThat(XmlGeneratedNames.toFieldName("models-service")).isEqualTo("models_service");
    }
}
