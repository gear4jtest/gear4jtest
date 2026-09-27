package io.github.gear4jtest.studio.model;

public record Diagnostic(Severity severity, String code, String path, String message) {
    public enum Severity {
        ERROR, WARNING
    }

    public static Diagnostic error(String code, String path, String message) {
        return new Diagnostic(Severity.ERROR, code, path, message);
    }
}
