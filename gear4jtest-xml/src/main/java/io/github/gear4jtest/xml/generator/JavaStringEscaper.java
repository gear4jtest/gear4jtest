package io.github.gear4jtest.xml.generator;

/** Utility methods for Java source string literal generation. */
final class JavaStringEscaper {
    private JavaStringEscaper() {
    }

    static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
