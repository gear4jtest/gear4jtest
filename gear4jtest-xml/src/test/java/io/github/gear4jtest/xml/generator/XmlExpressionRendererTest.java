package io.github.gear4jtest.xml.generator;

import io.github.gear4jtest.xml.model.XmlPipelineDefinition.Condition;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition.ValueParameter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlExpressionRendererTest {
    private final JavaImportManager imports = new JavaImportManager("io.test.generated");
    private final XmlExpressionRenderer renderer = new XmlExpressionRenderer(XmlJavaSourcePolicy.trusted());

    @Test
    void valueExpression_shouldEscapeStringLiterals() {
        // Given
        ValueParameter parameter = new ValueParameter(null, "a\"b\\c\n", "java.lang.String");

        // When / Then
        assertThat(renderer.valueExpression(parameter)).isEqualTo("\"a\\\"b\\\\c\\n\"");
    }

    @Test
    void conditionLambda_shouldKeepExplicitLambdaAsIs() {
        // Given / When / Then
        assertThat(renderer.conditionLambda(new Condition("value -> value != null", null), imports))
                .isEqualTo("value -> value != null");
    }

    @Test
    void conditionLambda_shouldRenderGelConditionWithoutInlineJavaValidation() {
        // Given / When / Then
        assertThat(renderer.conditionLambda(new Condition("input.enabled == true", Condition.LANGUAGE_GEL, null),
                                            imports))
                .isEqualTo("(input, ctx) -> evaluateGel(\"input.enabled == true\", input, ctx)");
    }

    @Test
    void conditionLambda_shouldRejectUnsupportedExpressionLanguage() {
        // Given
        Condition condition = new Condition("input.ok", "spel", null);

        // When / Then
        assertThatThrownBy(() -> renderer.conditionLambda(condition, imports))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported XML condition language");
    }

    @Test
    void normalizeExpression_shouldRegisterStaticCollectorImport() {
        // Given / When
        String normalized = renderer.normalizeExpression("java.util.stream.Collectors.toList()", imports);

        // Then
        assertThat(normalized).isEqualTo("toList()");
        assertThat(imports.renderImports()).contains("import static java.util.stream.Collectors.toList;");
    }
}
