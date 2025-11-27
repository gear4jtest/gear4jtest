package io.github.gear4jtest.core.exception;

public final class SideComputeExecutionException extends RuntimeException {
    public SideComputeExecutionException(String key, Throwable cause) {
        super("Side compute '" + key + "' failed for key '" + key + "'", cause);
    }
}
