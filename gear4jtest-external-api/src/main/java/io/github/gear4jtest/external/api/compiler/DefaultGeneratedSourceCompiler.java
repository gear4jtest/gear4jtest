package io.github.gear4jtest.external.api.compiler;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import io.github.gear4jtest.external.api.exception.CompilationException;

/**
 * Built-in compiler that selects one backend when it is created.
 */
final class DefaultGeneratedSourceCompiler implements GeneratedSourceCompiler {
    private final GeneratedSourceCompiler delegate;
    private final String backendName;

    DefaultGeneratedSourceCompiler(ClassLoader parentClassLoader) {
        this(JavaxToolsGeneratedSourceCompiler.isAvailable(),
                () -> new JavaxToolsGeneratedSourceCompiler(parentClassLoader),
                () -> new JDTInMemoryCompiler(parentClassLoader));
    }

    DefaultGeneratedSourceCompiler(boolean javacAvailable,
                                   Supplier<? extends GeneratedSourceCompiler> javacFactory,
                                   Supplier<? extends GeneratedSourceCompiler> jdtFactory) {
        Objects.requireNonNull(javacFactory, "javacFactory must not be null");
        Objects.requireNonNull(jdtFactory, "jdtFactory must not be null");
        this.backendName = javacAvailable ? "javac" : "JDT";
        this.delegate = Objects.requireNonNull(
                                               javacAvailable ? javacFactory.get() : jdtFactory.get(),
                                               backendName + " compiler must not be null");
    }

    @Override
    public Map<String, byte[]> compile(String className, byte[] sourceCode) {
        try {
            return delegate.compile(className, sourceCode);
        } catch (CompilationException failure) {
            throw new CompilationException(
                    "Default generated-source compilation failed using " + backendName + " for " + className,
                    failure.diagnostics(), failure);
        }
    }

    String backendName() {
        return backendName;
    }
}
