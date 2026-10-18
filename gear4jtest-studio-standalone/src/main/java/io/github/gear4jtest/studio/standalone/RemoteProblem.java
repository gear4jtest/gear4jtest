package io.github.gear4jtest.studio.standalone;

/**
 * Value-free transport failure. An uncertain mutation must never be
 * automatically replayed.
 */
public final class RemoteProblem extends RuntimeException {
    private final int status;

    RemoteProblem(String code, int status) {
        super(code);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
