package io.test.gear4jtest.xml.generator;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.test.gear4test.xml.generator.AssemblyLineGenerator;
import io.test.gear4test.xml.validator.AssemblyLineValidator;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineGeneratorTest {

    @Test
    void testXsdGenerationSuccessful() throws IOException {
        // Given
        InputStream io = getClass().getResourceAsStream("/samples/sample-assembly-line.xml");

        // When
        TypeSpec typeSpec = AssemblyLineGenerator.generateFlatAssemblyLines(io);
        JavaFile javaFile = JavaFile.builder("whatever", typeSpec)
                .indent("    ")
                .skipJavaLangImports(true)
                .addStaticImport(ElementModelBuilders.class, "*")
                .build();
        javaFile.writeTo(new File("/tmp/a.java"));
        // Then
        assertThat(typeSpec);
    }
}
