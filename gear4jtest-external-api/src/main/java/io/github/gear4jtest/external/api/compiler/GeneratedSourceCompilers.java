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
     * Returns Gear4J's built-in default compiler for generated Java source.
     *
     * <p>
     * The standard JDK {@code javax.tools.JavaCompiler} is preferred when the
     * runtime image provides it. Eclipse JDT remains the fallback for stripped
     * runtime images or deployments that do not include {@code jdk.compiler}.
     * </p>
     */
    public static GeneratedSourceCompiler defaultCompiler() {
        return defaultCompiler(contextClassLoader());
    }

    public static GeneratedSourceCompiler defaultCompiler(ClassLoader parentClassLoader) {
        ClassLoader effectiveClassLoader = parentClassLoader != null ? parentClassLoader : contextClassLoader();
        return new DefaultGeneratedSourceCompiler(effectiveClassLoader);
    }

    /**
     * Loads the first {@link GeneratedSourceCompiler} provider visible from the
     * supplied classloader. Falls back to the built-in default compiler when no
     * provider is registered.
     */
    public static GeneratedSourceCompiler fromServiceLoader(ClassLoader classLoader) {
        ClassLoader effectiveClassLoader = classLoader != null ? classLoader : contextClassLoader();
        var compilers = ServiceLoader.load(GeneratedSourceCompiler.class, effectiveClassLoader).iterator();
        return compilers.hasNext() ? compilers.next() : defaultCompiler(effectiveClassLoader);
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }
}
