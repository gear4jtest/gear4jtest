package io.github.gear4jtest.external.api.compiler;

import java.util.Map;

/**
 * Compiles generated Java source into in-memory class bytes.
 *
 * <p>
 * This SPI isolates Gear4J from a concrete compiler implementation. The default
 * implementation is {@link JDTInMemoryCompiler}, but callers can provide
 * another compiler without coupling {@code AssemblyLineManager} to Eclipse JDT
 * internals.
 * </p>
 */
@FunctionalInterface
public interface GeneratedSourceCompiler {
    /**
     * Compiles one generated Java source unit.
     *
     * @param className  fully-qualified generated class name
     * @param sourceCode UTF-8 Java source bytes
     * @return compiled class bytes keyed by fully-qualified class name
     */
    Map<String, byte[]> compile(String className, byte[] sourceCode);
}
