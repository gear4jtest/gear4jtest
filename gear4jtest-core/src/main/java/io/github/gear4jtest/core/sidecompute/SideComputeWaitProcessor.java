package io.github.gear4jtest.core.sidecompute;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.github.gear4jtest.core.api.behavior.Processor;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.exception.SideComputeExecutionException;
import io.github.gear4jtest.core.exception.SideComputeTimeoutException;

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
        var future = execCtx.getSideComputeContext().getOrCreateFuture(key);

        Duration effectiveTimeout = timeout != null ? timeout : safetyTimeout;

        try {
            Object result = future.get(effectiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
            execCtx.getContext().put(SideComputeKeys.valueKey(key), result);

        } catch (TimeoutException te) {
            switch (onTimeout) {
                case FAIL_PIPELINE -> {
                    future.completeExceptionally(te);
                    throw new SideComputeTimeoutException(key, effectiveTimeout, te);
                }
                case USE_FALLBACK -> {
                    Object fb = fallback != null ? fallback.get() : null;
                    future.complete(fb);
                    execCtx.getContext().put(SideComputeKeys.valueKey(key), fb);
                }
                case IGNORE -> {
                    // Leave the value unresolved; parameter resolution can observe its absence.
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SideComputeExecutionException(key, ex);
        } catch (Exception ex) {
            // Other future failures are wrapped as side-compute execution failures.
            throw new SideComputeExecutionException(key, ex);
        }
    }

    @Override
    public void afterExecution(Object result, StationExecutionContext context) {
        // Nothing to do: the resolved value is stored in the global context for
        // parameters to consume.
    }

    public enum OnTimeout {
        FAIL_PIPELINE, USE_FALLBACK, IGNORE
    }

    public static final class Builder {
        private final String key;
        private Duration timeout;
        private Duration safetyTimeout = DEFAULT_SAFETY_TIMEOUT;
        private OnTimeout onTimeout = OnTimeout.FAIL_PIPELINE;
        private Supplier<?> fallback;

        private Builder(String key) {
            this.key = key;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder safetyTimeout(Duration safetyTimeout) {
            this.safetyTimeout = Objects.requireNonNull(safetyTimeout, "safetyTimeout must not be null");
            return this;
        }

        public Builder onTimeoutFail() {
            this.onTimeout = OnTimeout.FAIL_PIPELINE;
            this.fallback = null;
            return this;
        }

        public Builder onTimeoutUseFallback(Supplier<?> fallback) {
            this.onTimeout = OnTimeout.USE_FALLBACK;
            this.fallback = fallback;
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
    }
}
