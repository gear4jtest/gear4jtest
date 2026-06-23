package io.github.gear4jtest.core.api.util;

import io.github.gear4jtest.core.api.behavior.BaseError;
import io.github.gear4jtest.core.api.behavior.SignalType;

/**
 * Builders for station error policies.
 */
public final class Errors {
    private Errors() {
    }

    public static <T> BaseError.UnSafeError.Builder<T> ignore(Class<? extends Throwable> throwableType) {
        return new BaseError.UnSafeError.Builder<>(SignalType.IGNORE, throwableType);
    }

    public static <T> BaseError.SafeError.Builder<T> fatal(Class<? extends Throwable> throwableType) {
        return new BaseError.SafeError.Builder<>(SignalType.FATAL, throwableType);
    }

    public static <T> BaseError.SafeError.Builder<T> stop(Class<? extends Throwable> throwableType) {
        return new BaseError.SafeError.Builder<>(SignalType.STOP, throwableType);
    }
}
