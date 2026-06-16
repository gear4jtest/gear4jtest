package io.github.gear4jtest.external.api.compiler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import io.github.gear4jtest.external.api.exception.CompilationException;

/**
 * Alternative compiler implementation based on the JDK {@link JavaCompiler}
 * API. It is useful when applications prefer the standard javac toolchain over
 * Eclipse JDT internals.
 */
public final class JavaxToolsGeneratedSourceCompiler implements GeneratedSourceCompiler {
    private final ClassLoader parentClassLoader;

    public JavaxToolsGeneratedSourceCompiler() {
        this(contextClassLoader());
    }

    public JavaxToolsGeneratedSourceCompiler(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader != null ? parentClassLoader : ClassLoader.getSystemClassLoader();
    }

    @Override
    public Map<String, byte[]> compile(String className, byte[] sourceCode) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CompilationException("JDK JavaCompiler is not available. Use a JDK runtime or the JDT compiler.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(diagnostics,
                                                                                           Locale.ROOT,
                                                                                           StandardCharsets.UTF_8)) {
            InMemoryFileManager fileManager = new InMemoryFileManager(standardFileManager);
            JavaFileObject source = new SourceFileObject(className, sourceCode);
            List<String> options = compilerOptions();
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null,
                                                                 List.of(source));
            Boolean succeeded = task.call();
            if (!Boolean.TRUE.equals(succeeded)) {
                throw new CompilationException("javac compilation failed for " + className,
                        formatDiagnostics(diagnostics));
            }
            Map<String, byte[]> compiledClasses = fileManager.compiledClasses();
            if (compiledClasses.isEmpty()) {
                throw new CompilationException("javac compilation produced no class for " + className);
            }
            return compiledClasses;
        } catch (IOException e) {
            throw new CompilationException("javac compilation failed for " + className, List.of(e.getMessage()), e);
        }
    }

    public ClassLoader parentClassLoader() {
        return parentClassLoader;
    }

    private static List<String> compilerOptions() {
        List<String> options = new ArrayList<>();
        options.add("--release");
        options.add("17");
        options.add("-encoding");
        options.add(StandardCharsets.UTF_8.name());
        String classPath = System.getProperty("java.class.path");
        if (classPath != null && !classPath.isBlank()) {
            options.add("-classpath");
            options.add(classPath);
        }
        return options;
    }

    private static List<String> formatDiagnostics(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(JavaxToolsGeneratedSourceCompiler::formatDiagnostic)
                .toList();
    }

    private static String formatDiagnostic(Diagnostic<? extends JavaFileObject> diagnostic) {
        return "%s:%d:%d: %s".formatted(diagnostic.getSource() == null ? "<unknown>" : diagnostic.getSource().getName(),
                                        diagnostic.getLineNumber(), diagnostic.getColumnNumber(),
                                        diagnostic.getMessage(Locale.ROOT));
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    private static final class SourceFileObject extends SimpleJavaFileObject {
        private final byte[] sourceCode;

        private SourceFileObject(String className, byte[] sourceCode) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.sourceCode = sourceCode.clone();
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return new String(sourceCode, StandardCharsets.UTF_8);
        }
    }

    private static final class ByteCodeFileObject extends SimpleJavaFileObject {
        private final ByteArrayOutputStream byteCode = new ByteArrayOutputStream();

        private ByteCodeFileObject(String className, Kind kind) {
            super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
        }

        @Override
        public ByteArrayOutputStream openOutputStream() {
            return byteCode;
        }

        byte[] toByteArray() {
            return byteCode.toByteArray();
        }
    }

    private static final class InMemoryFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteCodeFileObject> outputs = new HashMap<>();

        private InMemoryFileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location,
                                                   String className,
                                                   JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            ByteCodeFileObject output = new ByteCodeFileObject(className, kind);
            outputs.put(className, output);
            return output;
        }

        Map<String, byte[]> compiledClasses() {
            Map<String, byte[]> compiled = new HashMap<>();
            outputs.forEach((className, output) -> compiled.put(className, output.toByteArray()));
            return compiled;
        }
    }
}
