package io.github.gear4jtest.external.api.compiler;

import java.util.ServiceLoader;

/**
 * Factory methods for generated-source compiler implementations.
 */
public final class GeneratedSourceCompilers {
    private GeneratedSourceCompilers() {
    }

    public static GeneratedSourceCompiler jdt() {
        return jdt(contextClassLoader());
    }

    public static GeneratedSourceCompiler jdt(ClassLoader parentClassLoader) {
        return new JDTInMemoryCompiler(parentClassLoader);
    }

    public static GeneratedSourceCompiler javac() {
        return javac(contextClassLoader());
    }

    public static GeneratedSourceCompiler javac(ClassLoader parentClassLoader) {
        return new JavaxToolsGeneratedSourceCompiler(parentClassLoader);
    }

    /**
     * Loads the first {@link GeneratedSourceCompiler} provider visible from the
     * supplied classloader. Falls back to the JDT implementation when no provider
     * is registered.
     */
    public static GeneratedSourceCompiler fromServiceLoader(ClassLoader classLoader) {
        ClassLoader effectiveClassLoader = classLoader != null ? classLoader : contextClassLoader();
        for (GeneratedSourceCompiler compiler : ServiceLoader.load(GeneratedSourceCompiler.class,
                                                                   effectiveClassLoader)) {
            return compiler;
        }
        return jdt(effectiveClassLoader);
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }
}
