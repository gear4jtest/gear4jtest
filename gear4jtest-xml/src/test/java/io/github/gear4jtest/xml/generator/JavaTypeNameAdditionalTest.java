package io.github.gear4jtest.xml.generator;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaTypeNameAdditionalTest {
    @Test
    void parse_shouldNormalizeCommonShortNamesAndRenderImports() {
        // Given
        JavaImportManager imports = new JavaImportManager("io.test.generated");

        // When
        JavaTypeName type = JavaTypeName.parse("Map<String, List<Integer>>");

        // Then
        assertThat(type.canonical()).isEqualTo("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");
        assertThat(type.render(imports)).isEqualTo("Map<String, List<Integer>>");
        assertThat(imports.renderImports()).contains("import java.util.List;").contains("import java.util.Map;");
    }

    @Test
    void parse_shouldFallbackToObjectForBlankInputAndRejectInvalidSyntax() {
        assertThat(JavaTypeName.parse(null)).isEqualTo(JavaTypeName.OBJECT);
        assertThat(JavaTypeName.parse("  ")).isEqualTo(JavaTypeName.OBJECT);
        assertThatThrownBy(() -> JavaTypeName.parse("<String>")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Java type: <String>");
        assertThatThrownBy(() -> JavaTypeName.parse("List<String"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expected '>' in Java type: List<String");
    }

    @Test
    void render_shouldHandleArraysAndClassLiterals() {
        // Given
        JavaImportManager imports = new JavaImportManager("io.test.generated");
        JavaTypeName array = JavaTypeName.raw("java.lang.String[]");

        // Then
        assertThat(array.render(imports)).isEqualTo("String[]");
        assertThat(array.renderClassLiteral(imports)).isEqualTo("String[].class");
        assertThat(JavaTypeName.parameterized("java.util.Set", JavaTypeName.STRING).firstArgumentOrObject())
                .isEqualTo(JavaTypeName.STRING);
        assertThat(JavaTypeName.raw("java.util.Set").firstArgumentOrObject()).isEqualTo(JavaTypeName.OBJECT);
        assertThat(JavaTypeName.raw("java.util.Set").isIterableLike()).isTrue();
        assertThat(JavaTypeName.raw("java.util.Map").isIterableLike()).isFalse();
    }

    @Test
    void from_shouldResolveWildcardsAndGenericArrays() throws NoSuchFieldException {
        // Given
        JavaImportManager imports = new JavaImportManager("io.test.generated");

        // When / Then
        assertThat(JavaTypeName.from(firstTypeArgument("superNumbers")).render(imports)).isEqualTo("? super Number");
        assertThat(JavaTypeName.from(firstTypeArgument("extendedNumbers")).render(imports))
                .isEqualTo("? extends Number");
        assertThat(JavaTypeName.from(firstTypeArgument("anyValues")).render(imports)).isEqualTo("?");
        assertThat(JavaTypeName.from(genericArrayType()).canonical()).isEqualTo("java.util.List<java.lang.String>[]");
        assertThat(JavaTypeName.from(String[].class).canonical()).isEqualTo("java.lang.String[]");
        assertThat(JavaTypeName.from(GenericHolder.class.getTypeParameters()[0])).isEqualTo(JavaTypeName.OBJECT);
    }

    @Test
    void parse_shouldNormalizeAllBuiltInAliases() {
        assertThat(JavaTypeName.parse("String").canonical()).isEqualTo("java.lang.String");
        assertThat(JavaTypeName.parse("Integer").canonical()).isEqualTo("java.lang.Integer");
        assertThat(JavaTypeName.parse("Long").canonical()).isEqualTo("java.lang.Long");
        assertThat(JavaTypeName.parse("Boolean").canonical()).isEqualTo("java.lang.Boolean");
        assertThat(JavaTypeName.parse("Double").canonical()).isEqualTo("java.lang.Double");
        assertThat(JavaTypeName.parse("Float").canonical()).isEqualTo("java.lang.Float");
        assertThat(JavaTypeName.parse("Short").canonical()).isEqualTo("java.lang.Short");
        assertThat(JavaTypeName.parse("Byte").canonical()).isEqualTo("java.lang.Byte");
        assertThat(JavaTypeName.parse("Character").canonical()).isEqualTo("java.lang.Character");
        assertThat(JavaTypeName.parse("Object").canonical()).isEqualTo("java.lang.Object");
        assertThat(JavaTypeName.parse("List<String>").canonical())
                .isEqualTo("java.util.List<java.lang.String>");
        assertThat(JavaTypeName.parse("Set<String>").canonical())
                .isEqualTo("java.util.Set<java.lang.String>");
    }

    @Test
    void render_shouldKeepSamePackageAndJavaLangTypesUnimported() {
        // Given
        JavaImportManager imports = new JavaImportManager("io.test.generated");

        // When / Then
        assertThat(JavaTypeName.parse("io.test.generated.LocalType").render(imports)).isEqualTo("LocalType");
        assertThat(JavaTypeName.parse("java.lang.Integer").render(imports)).isEqualTo("Integer");
        assertThat(imports.renderImports()).isEmpty();
    }

    private static Type firstTypeArgument(String fieldName) throws NoSuchFieldException {
        ParameterizedType type = (ParameterizedType) TypeSamples.class.getDeclaredField(fieldName).getGenericType();
        return type.getActualTypeArguments()[0];
    }

    private static GenericArrayType genericArrayType() throws NoSuchFieldException {
        Field field = TypeSamples.class.getDeclaredField("genericArray");
        return (GenericArrayType) field.getGenericType();
    }

    private static final class TypeSamples<T> {
        private List<? super Number> superNumbers;
        private List<? extends Number> extendedNumbers;
        private List<?> anyValues;
        private List<String>[] genericArray;
    }

    private static final class GenericHolder<T> {
    }
}
