package io.github.gear4jtest.external.api.compiler;

import java.nio.charset.StandardCharsets;

import io.github.gear4jtest.external.api.exception.CompilationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedSourceCompilersTest {
    @Test
    void defaultCompiler_shouldReturnBuiltInFallbackCompiler() {
        // When
        GeneratedSourceCompiler compiler = GeneratedSourceCompilers.defaultCompiler(getClass().getClassLoader());

        // Then
        assertThat(compiler).isInstanceOf(DefaultGeneratedSourceCompiler.class);
    }

    @Test
    void fromServiceLoader_shouldFallbackToDefaultCompilerWhenNoProviderIsRegistered() {
        // When
        GeneratedSourceCompiler compiler = GeneratedSourceCompilers.fromServiceLoader(getClass().getClassLoader());

        // Then
        assertThat(compiler).isInstanceOf(DefaultGeneratedSourceCompiler.class);
    }

    @Test
    void defaultCompiler_shouldCompileGeneratedSource() {
        // Given
        GeneratedSourceCompiler compiler = GeneratedSourceCompilers.defaultCompiler(getClass().getClassLoader());
        String source = """
                package io.github.gear4jtest.external.api.generated;

                public final class DefaultCompilerGeneratedLine implements io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine {
                    @Override
                    public io.github.gear4jtest.core.api.AssemblyLine getAssemblyLineDefinition() {
                        return null;
                    }
                }
                """;

        // When / Then
        assertThat(compiler.compile("io.github.gear4jtest.external.api.generated.DefaultCompilerGeneratedLine",
                                    source.getBytes(StandardCharsets.UTF_8)))
                .containsKey("io.github.gear4jtest.external.api.generated.DefaultCompilerGeneratedLine");
    }

    @Test
    void defaultCompiler_shouldReportDiagnosticsWhenAllCompilersRejectSource() {
        // Given
        GeneratedSourceCompiler compiler = GeneratedSourceCompilers.defaultCompiler(getClass().getClassLoader());
        String invalidSource = "package io.github.gear4jtest.external.api.generated; public class Broken {";

        // When / Then
        assertThatThrownBy(() -> compiler.compile("io.github.gear4jtest.external.api.generated.Broken",
                                                  invalidSource.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("Default generated-source compilation failed");
    }

    @Test
    void explicitFactories_shouldReturnRequestedCompilerImplementations() {
        // Given
        ClassLoader parent = getClass().getClassLoader();

        // When / Then
        assertThat(GeneratedSourceCompilers.jdt(parent)).isInstanceOf(JDTInMemoryCompiler.class);
        assertThat(GeneratedSourceCompilers.javac(parent)).isInstanceOf(JavaxToolsGeneratedSourceCompiler.class);
        assertThat(GeneratedSourceCompilers.defaultCompiler(null))
                .isInstanceOf(DefaultGeneratedSourceCompiler.class);
        assertThat(GeneratedSourceCompilers.fromServiceLoader(null))
                .isInstanceOf(DefaultGeneratedSourceCompiler.class);
    }

    @Test
    void noArgumentFactories_shouldUseContextClassLoaderFallback() {
        // Given
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            // When / Then
            assertThat(GeneratedSourceCompilers.jdt()).isInstanceOf(JDTInMemoryCompiler.class);
            assertThat(GeneratedSourceCompilers.javac()).isInstanceOf(JavaxToolsGeneratedSourceCompiler.class);
            assertThat(GeneratedSourceCompilers.defaultCompiler()).isInstanceOf(DefaultGeneratedSourceCompiler.class);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

}
