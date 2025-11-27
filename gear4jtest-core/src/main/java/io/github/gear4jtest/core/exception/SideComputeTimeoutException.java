package io.github.gear4jtest.core.exception;

import java.time.Duration;

public final class SideComputeTimeoutException extends RuntimeException {
    public SideComputeTimeoutException(String key, Duration timeout, Throwable cause) {
        super("Side compute '" + key + "' timed out after " + timeout, cause);
    }
}
