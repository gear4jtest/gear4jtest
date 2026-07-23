package io.github.gear4jtest.external.api.compiler;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

import io.github.gear4jtest.external.api.loader.InMemoryClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaxToolsGeneratedSourceCompilerTest {
    @TempDir
    Path temporaryDirectory;

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

    @Test
    void compile_shouldResolveTypesVisibleOnlyFromParentUrlClassLoader() throws Exception {
        // Given
        Path sourceRoot = temporaryDirectory.resolve("parent-source");
        Path classesRoot = temporaryDirectory.resolve("parent-classes");
        Path parentSource = sourceRoot.resolve("example/parent/ParentOnlyType.java");
        Files.createDirectories(parentSource.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(parentSource, """
                package example.parent;
                public final class ParentOnlyType {
                    public String value() { return "parent"; }
                }
                """, StandardCharsets.UTF_8);
        int exitCode = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                                                                "--release", "17", "-d", classesRoot.toString(),
                                                                parentSource.toString());
        assertThat(exitCode).isZero();

        try (URLClassLoader parent = new URLClassLoader(
                new java.net.URL[] { classesRoot.toUri().toURL() }, getClass().getClassLoader())) {
            JavaxToolsGeneratedSourceCompiler compiler = new JavaxToolsGeneratedSourceCompiler(parent);
            String source = """
                    package example.generated;
                    public final class UsesParentOnlyType {
                        private final example.parent.ParentOnlyType dependency =
                                new example.parent.ParentOnlyType();
                        public String value() { return dependency.value(); }
                    }
                    """;

            // When
            var classes = compiler.compile("example.generated.UsesParentOnlyType",
                                           source.getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(classes).containsKey("example.generated.UsesParentOnlyType");
        }
    }

    @Test
    void compile_shouldRejectApisNewerThanJava17() {
        // Given
        JavaxToolsGeneratedSourceCompiler compiler = new JavaxToolsGeneratedSourceCompiler(
                Thread.currentThread().getContextClassLoader());
        String source = """
                package example.generated;
                public final class UsesPost17Api {
                    public Thread create() { return Thread.ofVirtual().unstarted(() -> { }); }
                }
                """;

        // When / Then
        assertThatThrownBy(() -> compiler.compile("example.generated.UsesPost17Api",
                                                  source.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(io.github.gear4jtest.external.api.exception.CompilationException.class)
                .hasMessageContaining("javac compilation failed");
    }

}
