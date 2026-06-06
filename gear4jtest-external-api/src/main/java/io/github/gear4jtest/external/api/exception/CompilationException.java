package io.github.gear4jtest.external.api.exception;

import java.util.List;

public class CompilationException extends RuntimeException {
    private final List<String> diagnostics;

    public CompilationException(String message) {
        this(message, List.of(), null);
    }

    public CompilationException(Throwable cause) {
        this(cause == null ? "Compilation failed" : cause.getMessage(), List.of(), cause);
    }

    public CompilationException(String message, List<String> diagnostics) {
        this(message, diagnostics, null);
    }

    public CompilationException(String message, List<String> diagnostics, Throwable cause) {
        super(messageWithDiagnostics(message, diagnostics), cause);
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    private static String messageWithDiagnostics(String message, List<String> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return message;
        }
        return message + System.lineSeparator() + String.join(System.lineSeparator(), diagnostics);
    }

    public List<String> diagnostics() {
        return diagnostics;
    }
}
