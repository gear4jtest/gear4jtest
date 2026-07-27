package io.github.gear4jtest.xml.generator;

import java.util.List;

import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.ContainerOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Dependency;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.Operation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SignalOperation;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition.SubLine;
import io.github.gear4jtest.xml.validator.XmlDefinitionValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class XmlDefinitionSemanticValidatorTest {
    private final XmlToJavaGenerator generator = XmlToJavaGenerator.trusted();

    @Test
    void generate_shouldRejectDependencyNameThatNormalizesToJavaKeyword() {
        // Given
        XmlAssemblyLineDefinition definition = definition(
                                                          List.of(new Dependency("class", "java.lang.String")),
                                                          signal("stop"));

        // When
        Throwable failure = catchThrowable(() -> generator.generate(definition));

        // Then
        assertThat(failure).isInstanceOf(XmlDefinitionValidationException.class)
                .hasMessageContaining("/assemblyLine/dependencies/dependency[1]/@name")
                .hasMessageContaining("value 'class'")
                .hasMessageContaining("not a valid Java 17 identifier");
        XmlDefinitionValidationException validationFailure = (XmlDefinitionValidationException) failure;
        assertThat(validationFailure.path()).isEqualTo("/assemblyLine/dependencies/dependency[1]/@name");
        assertThat(validationFailure.rejectedValue()).isEqualTo("class");
    }

    @Test
    void generate_shouldRejectDependencyFieldCollisionAfterNormalization() {
        // Given
        XmlAssemblyLineDefinition definition = definition(
                                                          List.of(new Dependency("foo-bar", "java.lang.String"),
                                                                  new Dependency("foo_bar", "java.lang.String")),
                                                          signal("stop"));

        // When / Then
        assertThat(catchThrowable(() -> generator.generate(definition)))
                .isInstanceOf(XmlDefinitionValidationException.class)
                .hasMessageContaining("/assemblyLine/dependencies/dependency[2]/@name")
                .hasMessageContaining("Generated field name collision 'foo_bar'")
                .hasMessageContaining("/assemblyLine/dependencies/dependency[1]/@name");
    }

    @Test
    void generate_shouldRejectDependencyCollisionWithParallelExecutorField() {
        // Given
        ContainerOperation container = new ContainerOperation("parallel-container", "java.lang.String",
                "java.lang.String", true, 2, List.of(new SubLine("only", null, signal("nested-stop"))), null);
        XmlAssemblyLineDefinition definition = definition(
                                                          List.of(new Dependency(
                                                                  "gear4jParallel_containerExecutorService",
                                                                  "java.lang.String")),
                                                          container);

        // When / Then
        assertThat(catchThrowable(() -> generator.generate(definition)))
                .isInstanceOf(XmlDefinitionValidationException.class)
                .hasMessageContaining("/assemblyLine/operations/container[1]/@id")
                .hasMessageContaining("Generated field name collision 'gear4jParallel_containerExecutorService'")
                .hasMessageContaining("/assemblyLine/dependencies/dependency[1]/@name");
    }

    @Test
    void generate_shouldRejectTrailingTextInTypeWithXmlPath() {
        // Given
        XmlAssemblyLineDefinition definition = new XmlAssemblyLineDefinition("pipeline",
                "java.lang.String trailing", "java.lang.String", List.of(signal("stop")), null, List.of());

        // When / Then
        assertThat(catchThrowable(() -> generator.generate(definition)))
                .isInstanceOf(XmlDefinitionValidationException.class)
                .hasMessageContaining("/assemblyLine/@inputType")
                .hasMessageContaining("value 'java.lang.String trailing'")
                .hasMessageContaining("invalid Java type")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_shouldRejectDuplicateContainerBranchIds() {
        // Given
        ContainerOperation container = new ContainerOperation("container", "java.lang.String", "java.lang.String",
                false, null,
                List.of(new SubLine("same", null, signal("first")),
                        new SubLine("same", null, signal("second"))),
                null);

        // When / Then
        assertThat(catchThrowable(() -> generator.generate(definition(List.of(), container))))
                .isInstanceOf(XmlDefinitionValidationException.class)
                .hasMessageContaining("/assemblyLine/operations/container[1]/subLines/subLine[2]/@id")
                .hasMessageContaining("Duplicate container branch id")
                .hasMessageContaining("subLine[1]/@id");
    }

    @Test
    void generate_shouldKeepNonKeywordNormalizedDependencyName() {
        // Given
        XmlAssemblyLineDefinition definition = definition(
                                                          List.of(new Dependency("models-service", "java.lang.String")),
                                                          signal("stop"));

        // When
        String source = generator.generate(definition).formattedSource();

        // Then
        assertThat(source).contains("@Inject(\"models-service\")")
                .contains("private String models_service;");
    }

    private static XmlAssemblyLineDefinition definition(List<Dependency> dependencies, Operation... operations) {
        return new XmlAssemblyLineDefinition("pipeline", "java.lang.String", "java.lang.String", List.of(operations),
                null, dependencies);
    }

    private static SignalOperation signal(String id) {
        return new SignalOperation(id, "STOP", "java.lang.String", null);
    }
}
