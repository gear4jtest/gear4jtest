package io.github.gear4jtest.external.api.compiler;

import java.nio.charset.StandardCharsets;

import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JavaxToolsGeneratedSourceCompilerTest {
    @Test
    void compile_shouldCompileGeneratedAssemblyLineWithJdkCompiler() throws Exception {
        // Given
        JavaxToolsGeneratedSourceCompiler compiler = new JavaxToolsGeneratedSourceCompiler(
                Thread.currentThread().getContextClassLoader());
        String source = """
                package io.github.gear4jtest.external.api.generated;

                public final class JavacGeneratedLine implements io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine {
                    @Override
                    public io.github.gear4jtest.core.api.AssemblyLine getAssemblyLineDefinition() {
                        return null;
                    }
                }
                """;

        // When
        var classes = compiler.compile("io.github.gear4jtest.external.api.generated.JavacGeneratedLine",
                                       source.getBytes(StandardCharsets.UTF_8));

        // Then
        assertThat(classes)
                .as("javac alternative compiler must produce the requested generated class")
                .containsKey("io.github.gear4jtest.external.api.generated.JavacGeneratedLine");

        InMemoryClassLoader classLoader = new InMemoryClassLoader(Thread.currentThread().getContextClassLoader());
        classLoader.addCompiledClasses(classes);
        Object instance = classLoader.createInstance("io.github.gear4jtest.external.api.generated.JavacGeneratedLine");
        assertThat(instance).isInstanceOf(io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine.class);
    }
}
