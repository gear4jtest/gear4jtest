package io.github.gear4jtest.external.api.compiler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.exception.CompilationException;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
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
    void defaultCompiler_shouldReportDiagnosticsFromSelectedBackend() {
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

    @Test
    void defaultCompiler_shouldSelectJavacOnceAndNeverRetrySourceErrorsWithJdt() {
        // Given
        AtomicInteger javacCreations = new AtomicInteger();
        AtomicInteger jdtCreations = new AtomicInteger();
        AtomicInteger javacCompilations = new AtomicInteger();
        var javacFailure = new CompilationException("javac rejected source", List.of("syntax error"));
        DefaultGeneratedSourceCompiler compiler = new DefaultGeneratedSourceCompiler(true,
                () -> {
                    javacCreations.incrementAndGet();
                    return (className, sourceCode) -> {
                        javacCompilations.incrementAndGet();
                        throw javacFailure;
                    };
                },
                () -> {
                    jdtCreations.incrementAndGet();
                    return (className, sourceCode) -> Map.of(className, new byte[] { 1 });
                });

        // When / Then
        assertThatThrownBy(() -> compiler.compile("io.test.Broken", new byte[] { 1 }))
                .isInstanceOf(CompilationException.class)
                .hasMessageContaining("using javac")
                .hasCause(javacFailure);
        assertThat(javacCreations).hasValue(1);
        assertThat(javacCompilations).hasValue(1);
        assertThat(jdtCreations).hasValue(0);
        assertThat(compiler.backendName()).isEqualTo("javac");
    }

    @Test
    void defaultCompiler_shouldSelectJdtOnlyWhenJavacIsUnavailable() {
        // Given
        AtomicInteger javacCreations = new AtomicInteger();
        AtomicInteger jdtCreations = new AtomicInteger();
        DefaultGeneratedSourceCompiler compiler = new DefaultGeneratedSourceCompiler(false,
                () -> {
                    javacCreations.incrementAndGet();
                    return (className, sourceCode) -> Map.of();
                },
                () -> {
                    jdtCreations.incrementAndGet();
                    return (className, sourceCode) -> Map.of(className, new byte[] { 1 });
                });

        // When
        var compiled = compiler.compile("io.test.Generated", new byte[] { 1 });

        // Then
        assertThat(compiled).containsKey("io.test.Generated");
        assertThat(javacCreations).hasValue(0);
        assertThat(jdtCreations).hasValue(1);
        assertThat(compiler.backendName()).isEqualTo("JDT");
    }

    @Test
    void jdtCompiler_shouldEnableJava17ReleaseSemantics() {
        assertThat(JDTInMemoryCompiler.compilerOptionValues())
                .containsEntry(CompilerOptions.OPTION_Source, CompilerOptions.VERSION_17)
                .containsEntry(CompilerOptions.OPTION_TargetPlatform, CompilerOptions.VERSION_17)
                .containsEntry(CompilerOptions.OPTION_Compliance, CompilerOptions.VERSION_17)
                .containsEntry(CompilerOptions.OPTION_Release, CompilerOptions.ENABLED);
    }

}
