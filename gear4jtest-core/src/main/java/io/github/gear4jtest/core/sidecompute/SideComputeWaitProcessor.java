package io.github.gear4jtest.core.sidecompute;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.exception.SideComputeExecutionException;
import io.github.gear4jtest.core.exception.SideComputeTimeoutException;

/**
 * Processor that blocks station execution until a named side-compute value is
 * available in the run context.
 *
 * <p>
 * The wait happens on the station thread. The side-compute itself is completed
 * by the event runtime, so applications should size the reaction executor and
 * choose explicit timeouts for side-compute workloads that are
 * latency-sensitive or may block on external systems.
 * </p>
 */
public final class SideComputeWaitProcessor implements Processor {
    /**
     * Safety net used when callers do not provide an explicit timeout.
     *
     * <p>
     * The previous behavior waited indefinitely via CompletableFuture.join(), which
     * could leak a worker thread forever if the side-compute never completed.
     */
    public static final Duration DEFAULT_SAFETY_TIMEOUT = Duration.ofMinutes(10);

    private final String key;
    private final Duration timeout;
    private final Duration safetyTimeout;
    private final OnTimeout onTimeout;
    private final Supplier<?> fallback;

    private SideComputeWaitProcessor(Builder builder) {
        this.key = builder.key;
        this.timeout = builder.timeout;
        this.safetyTimeout = builder.safetyTimeout;
        this.onTimeout = builder.onTimeout;
        this.fallback = builder.fallback;
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    @Override
    public FailureMode beforeExecutionFailureMode() {
        return FailureMode.FAIL_STATION;
    }

    @Override
    public <I> void beforeExecution(I input, StationExecutionContext opCtx) {
        ExecutionContext execCtx = opCtx.getGlobalContext();
        CompletableFuture<Object> future = execCtx.getSideComputeContext().getOrCreateFuture(key);

        Duration effectiveTimeout = timeout != null ? timeout : safetyTimeout;

        try {
            Object result = future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            storeResolvedValue(execCtx, result);

        } catch (TimeoutException te) {
            switch (onTimeout) {
                case FAIL_ASSEMBLY_LINE -> {
                    future.completeExceptionally(te);
                    throw new SideComputeTimeoutException(key, effectiveTimeout, te);
                }
                case USE_FALLBACK -> {
                    Object fallbackValue = resolveFallback(future);
                    storeResolvedValue(execCtx, fallbackValue);
                }
                case IGNORE -> {
                    // Leave the value unresolved; parameter resolution can observe its absence.
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SideComputeExecutionException(key, ex);
        } catch (SideComputeExecutionException ex) {
            throw ex;
        } catch (Exception ex) {
            // Other future failures are wrapped as side-compute execution failures.
            throw new SideComputeExecutionException(key, ex);
        }
    }

    private Object resolveFallback(CompletableFuture<Object> future) {
        Object fallbackValue;
        try {
            fallbackValue = fallback.get();
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
            throw new SideComputeExecutionException(key, exception);
        }
        if (fallbackValue == null) {
            IllegalStateException cause = new IllegalStateException(
                    "Side compute '" + key + "' fallback returned null; null results are not supported");
            future.completeExceptionally(cause);
            throw new SideComputeExecutionException(key, cause);
        }

        if (future.complete(fallbackValue)) {
            return fallbackValue;
        }
        try {
            return future.getNow(null);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            throw new SideComputeExecutionException(key, cause);
        }
    }

    private void storeResolvedValue(ExecutionContext executionContext, Object value) {
        if (value == null) {
            throw new SideComputeExecutionException(key, new IllegalStateException(
                    "Side compute '" + key + "' returned null; null results are not supported"));
        }
        executionContext.getContext().put(SideComputeKeys.valueKey(key), value);
    }

    @Override
    public void afterExecution(Object result, StationExecutionContext context) {
        // Nothing to do: the resolved value is stored in the global context for
        // parameters to consume.
    }

    public enum OnTimeout {
        FAIL_ASSEMBLY_LINE, USE_FALLBACK, IGNORE
    }

    public static final class Builder {
        private final String key;
        private Duration timeout;
        private Duration safetyTimeout = DEFAULT_SAFETY_TIMEOUT;
        private OnTimeout onTimeout = OnTimeout.FAIL_ASSEMBLY_LINE;
        private Supplier<?> fallback;

        private Builder(String key) {
            SideComputeKeys.validateUserKey(key);
            this.key = key;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout == null ? null : requirePositive(timeout, "timeout");
            return this;
        }

        Builder safetyTimeout(Duration safetyTimeout) {
            this.safetyTimeout = requirePositive(safetyTimeout, "safetyTimeout");
            return this;
        }

        public Builder onTimeoutFail() {
            this.onTimeout = OnTimeout.FAIL_ASSEMBLY_LINE;
            this.fallback = null;
            return this;
        }

        public Builder onTimeoutUseFallback(Supplier<?> fallback) {
            this.onTimeout = OnTimeout.USE_FALLBACK;
            this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
            return this;
        }

        public Builder onTimeoutIgnore() {
            this.onTimeout = OnTimeout.IGNORE;
            this.fallback = null;
            return this;
        }

        public SideComputeWaitProcessor build() {
            return new SideComputeWaitProcessor(this);
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be strictly positive");
            }
            return value;
        }
    }
}
