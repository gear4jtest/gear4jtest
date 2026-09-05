package io.github.gear4jtest.external.api.compiler;

import java.nio.charset.StandardCharsets;

import io.github.gear4jtest.external.api.exception.CompilationException;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdtCompilerCompatibilityTest {
    private static final int JAVA_17_CLASS_FILE_MAJOR_VERSION = 61;

    @Test
    void supportedJdtVersion_shouldCompileAndLoadJava17Source() throws Exception {
        // Given
        String className = "example.generated.JdtJava17Generated";
        String source = """
                package example.generated;

                public final class JdtJava17Generated {
                    private sealed interface Result permits Success { }
                    private record Success(String value) implements Result { }

                    public String render() {
                        Result result = new Success("JDT 17");
                        return result instanceof Success success ? success.value() : "unexpected";
                    }
                }
                """;
        GeneratedSourceCompiler compiler = GeneratedSourceCompilers.jdt(getClass().getClassLoader());

        // When
        var classes = compiler.compile(className, source.getBytes(StandardCharsets.UTF_8));

        // Then
        assertThat(classes).containsKeys(className, className + "$Result", className + "$Success");
        assertThat(classFileMajorVersion(classes.get(className))).isEqualTo(JAVA_17_CLASS_FILE_MAJOR_VERSION);

        InMemoryClassLoader classLoader = new InMemoryClassLoader(getClass().getClassLoader());
        classLoader.addCompiledClasses(classes);
        Object instance = classLoader.createInstance(className);
        assertThat(instance.getClass().getMethod("render").invoke(instance)).isEqualTo("JDT 17");
    }

    @Test
    void supportedJdtVersion_shouldExposeSourceDiagnostics() {
        // Given
        String className = "example.generated.JdtBroken";
        String source = "package example.generated; public final class JdtBroken {";
        GeneratedSourceCompiler compiler = GeneratedSourceCompilers.jdt(getClass().getClassLoader());

        // When / Then
        assertThatThrownBy(() -> compiler.compile(className, source.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CompilationException.class)
                .satisfies(error -> assertThat(((CompilationException) error).diagnostics())
                        .isNotEmpty()
                        .anyMatch(diagnostic -> diagnostic.contains("JdtBroken.java")));
    }

    private static int classFileMajorVersion(byte[] bytecode) {
        return (Byte.toUnsignedInt(bytecode[6]) << 8) | Byte.toUnsignedInt(bytecode[7]);
    }
}
