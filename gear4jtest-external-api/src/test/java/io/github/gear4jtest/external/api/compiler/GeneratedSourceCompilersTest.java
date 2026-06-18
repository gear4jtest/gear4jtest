package io.github.gear4jtest.external.api.compiler;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

}
