package io.github.gear4jtest.xml.generator;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaTypeNameDeepCoverageTest {
    @Test
    void parse_shouldHandleWhitespaceNestedTypesDollarNamesAndErase() {
        // Given
        JavaImportManager imports = new JavaImportManager("io.test.generated");

        // When
        JavaTypeName type = JavaTypeName.parse(" java.util.Map < String , java.util.Set < Long > > ");
        JavaTypeName nestedClass = JavaTypeName.parse("com.acme.Outer$Inner");

        // Then
        assertThat(type.canonical()).isEqualTo("java.util.Map<java.lang.String, java.util.Set<java.lang.Long>>");
        assertThat(type.render(imports)).isEqualTo("Map<String, Set<Long>>");
        assertThat(type.erase().canonical()).isEqualTo("java.util.Map");
        assertThat(nestedClass.rawType()).isEqualTo("com.acme.Outer$Inner");
    }

    @Test
    void parse_shouldRejectUnexpectedTrailingGenericSeparator() {
        assertThatThrownBy(() -> JavaTypeName.parse("Map<String, >"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Java type: Map<String, >");
    }

    @Test
    void render_shouldRenderParameterizedArraysAndClassLiteralsWithImports() {
        // Given
        JavaImportManager imports = new JavaImportManager("io.test.generated");
        JavaTypeName type = JavaTypeName.parameterized("java.util.List", JavaTypeName.raw("java.lang.String[]"));

        // When / Then
        assertThat(type.render(imports)).isEqualTo("List<String[]>");
        assertThat(type.renderClassLiteral(imports)).isEqualTo("List.class");
        assertThat(imports.renderImports()).contains("import java.util.List;");
    }

    @Test
    void from_shouldFallbackToObjectForUnknownTypesOrParameterizedTypesWithoutClassRawType() {
        // Given
        Type unknown = new Type() {};
        ParameterizedType nonClassRaw = new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return new Type[] { String.class };
            }

            @Override
            public Type getRawType() {
                return unknown;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };

        // When / Then
        assertThat(JavaTypeName.from(unknown)).isEqualTo(JavaTypeName.OBJECT);
        assertThat(JavaTypeName.from(nonClassRaw)).isEqualTo(JavaTypeName.OBJECT);
    }

    @Test
    void from_shouldRenderParameterizedTypesWithMultipleArguments() throws NoSuchFieldException {
        // Given
        JavaImportManager imports = new JavaImportManager("io.test.generated");
        Type type = TypeSamples.class.getDeclaredField("map").getGenericType();

        // When
        JavaTypeName javaType = JavaTypeName.from(type);

        // Then
        assertThat(javaType.canonical())
                .isEqualTo("java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>");
        assertThat(javaType.render(imports)).isEqualTo("Map<String, List<Integer>>");
    }

    private static final class TypeSamples {
        private Map<String, List<Integer>> map;
    }
}
