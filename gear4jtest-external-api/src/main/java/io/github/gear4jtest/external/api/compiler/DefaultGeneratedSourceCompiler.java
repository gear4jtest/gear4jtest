package io.github.gear4jtest.external.api.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.gear4jtest.external.api.exception.CompilationException;

/**
 * Built-in compiler that prefers the standard JDK compiler and falls back to
 * JDT.
 */
final class DefaultGeneratedSourceCompiler implements GeneratedSourceCompiler {
    private final JavaxToolsGeneratedSourceCompiler javac;
    private final JDTInMemoryCompiler jdt;

    DefaultGeneratedSourceCompiler(ClassLoader parentClassLoader) {
        this.javac = new JavaxToolsGeneratedSourceCompiler(parentClassLoader);
        this.jdt = new JDTInMemoryCompiler(parentClassLoader);
    }

    @Override
    public Map<String, byte[]> compile(String className, byte[] sourceCode) {
        CompilationException javacFailure = null;
        if (JavaxToolsGeneratedSourceCompiler.isAvailable()) {
            try {
                return javac.compile(className, sourceCode);
            } catch (CompilationException e) {
                javacFailure = e;
            }
        }

        try {
            return jdt.compile(className, sourceCode);
        } catch (CompilationException jdtFailure) {
            if (javacFailure == null) {
                throw jdtFailure;
            }
            List<String> diagnostics = new ArrayList<>();
            diagnostics.add("javac diagnostics:");
            diagnostics.addAll(javacFailure.diagnostics());
            diagnostics.add("JDT diagnostics:");
            diagnostics.addAll(jdtFailure.diagnostics());
            throw new CompilationException("Default generated-source compilation failed for " + className,
                    diagnostics, jdtFailure);
        }
    }
}
