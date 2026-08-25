package io.github.gear4jtest.external.api.compiler;

import java.util.Map;

import io.github.gear4jtest.core.api.annotation.Spi;

/**
 * Compiles generated Java source into in-memory class bytes.
 *
 * <p>
 * This SPI isolates Gear4J from a concrete compiler implementation. The default
 * strategy uses the JDK {@code javax.tools.JavaCompiler} when the runtime image
 * provides {@code jdk.compiler}, and falls back to Eclipse JDT otherwise.
 * Callers can also provide another compiler without coupling
 * {@code AssemblyLineManager} to compiler internals. A custom {@code jlink}
 * image that expects the javac backend must therefore include
 * {@code jdk.compiler}.
 * </p>
 */
@FunctionalInterface
@Spi
public interface GeneratedSourceCompiler {
    /**
     * Returns the stable identifier used for explicit SPI selection.
     */
    default String id() {
        return getClass().getName();
    }

    /**
     * Compiles one generated Java source unit.
     *
     * @param className  fully-qualified generated class name
     * @param sourceCode UTF-8 Java source bytes
     * @return compiled class bytes keyed by fully-qualified class name
     */
    Map<String, byte[]> compile(String className, byte[] sourceCode);
}
