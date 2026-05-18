package io.test.gear4jtest.xml.generator;

/** Formats Java source generated from external pipeline definitions. */
@FunctionalInterface
public interface JavaSourceFormatter {
    String format(String source);

    static JavaSourceFormatter none() {
        return source -> source;
    }
}
