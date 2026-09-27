package io.github.gear4jtest.studio.service;

/** Safe, value-free error code suitable for a management transport. */
public final class StudioProblem extends RuntimeException {
    private final Code code;

    public StudioProblem(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public enum Code {
        NOT_FOUND, CONFLICT, CAPACITY_EXCEEDED, INVALID_DEFINITION, UNSUPPORTED_FORMAT
    }
}
