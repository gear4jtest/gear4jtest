package io.github.gear4jtest.core.util;

import org.jspecify.annotations.Nullable;

/**
 * Controls exception details passed to framework loggers independently from
 * persistence capture. Original exceptions remain available to their callers.
 */
public final class ExceptionDiagnostics {
    /**
     * Process-wide opt-in to raw messages, stack traces, causes and suppressed
     * exceptions. Only the value {@code true}, ignoring case, enables it.
     */
    public static final String INCLUDE_DETAILS_PROPERTY = "gear4j.logging.includeExceptionDetails";

    private ExceptionDiagnostics() {
    }

    public static @Nullable Throwable forLogging(@Nullable Throwable failure) {
        if (failure == null || includesDetails()) {
            return failure;
        }
        // Do not inspect getMessage(), toString(), causes or user-provided stack
        // frames. The logger must never receive a reference to the original graph.
        return new WithheldException(failure.getClass().getName());
    }

    private static boolean includesDetails() {
        try {
            return Boolean.parseBoolean(System.getProperty(INCLUDE_DETAILS_PROPERTY));
        } catch (SecurityException ignored) {
            return false;
        }
    }

    private static final class WithheldException extends RuntimeException {
        private WithheldException(String exceptionType) {
            super("exceptionType=" + exceptionType + "; exception details withheld", null, false, false);
        }
    }
}
