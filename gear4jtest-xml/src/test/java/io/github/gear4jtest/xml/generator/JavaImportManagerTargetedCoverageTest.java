package io.github.gear4jtest.xml.generator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaImportManagerTargetedCoverageTest {
    @Test
    void use_shouldIgnoreBlankPrimitiveJavaLangAndSamePackageTypes() {
        JavaImportManager imports = new JavaImportManager("com.acme");

        assertThat(imports.use(null)).isNull();
        assertThat(imports.use(" ")).isEqualTo(" ");
        assertThat(imports.use("int")).isEqualTo("int");
        assertThat(imports.use("java.lang.String")).isEqualTo("String");
        assertThat(imports.use("com.acme.LocalType")).isEqualTo("LocalType");
        assertThat(imports.renderImports()).isEmpty();
    }

    @Test
    void use_shouldImportUniqueTypesButKeepConflictingFullyQualifiedNames() {
        JavaImportManager imports = new JavaImportManager("com.generated");

        assertThat(imports.use("java.util.List")).isEqualTo("List");
        assertThat(imports.use("java.awt.List")).isEqualTo("java.awt.List");
        imports.addStatic("java.util.Objects.requireNonNull");

        assertThat(imports.renderImports()).isEqualTo("""
                import java.util.List;

                import static java.util.Objects.requireNonNull;

                """);
    }
}
