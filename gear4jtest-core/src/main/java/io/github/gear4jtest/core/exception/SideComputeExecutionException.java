package io.github.gear4jtest.core.exception;

public final class SideComputeExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SideComputeExecutionException(String key, Throwable cause) {
        super("Side compute '" + key + "' failed", cause);
    }
}
