package io.github.gear4jtest.core.sidecompute;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.github.gear4jtest.core.exception.SideComputeExecutionException;
import io.github.gear4jtest.core.exception.SideComputeTimeoutException;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.behavior.Processor;

public final class SideComputeWaitProcessor implements Processor {

    @Override
    public FailureMode beforeExecutionFailureMode() {
        return FailureMode.FAIL_STATION;
    }

    public enum OnTimeout {
        FAIL_PIPELINE,
        USE_FALLBACK,
        IGNORE
    }

    private final String key;
    private final Duration timeout;
    private final OnTimeout onTimeout;
    private final Supplier<?> fallback;

    private SideComputeWaitProcessor(Builder builder) {
        this.key = builder.key;
        this.timeout = builder.timeout;
        this.onTimeout = builder.onTimeout;
        this.fallback = builder.fallback;
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final String key;
        private Duration timeout;
        private OnTimeout onTimeout = OnTimeout.FAIL_PIPELINE;
        private Supplier<?> fallback;

        private Builder(String key) {
            this.key = key;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

    @Override
    public <I> void beforeExecution(I input, StationExecutionContext opCtx) {
        ExecutionContext execCtx = opCtx.getGlobalContext();
        var future = execCtx.getSideComputeContext().getOrCreateFuture(key);

        try {
            Object result;
            if (timeout == null) {
                result = future.join();
            } else {
                result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            }

            execCtx.getContext().put(SideComputeKeys.valueKey(key), result);

        } catch (TimeoutException te) {
            switch (onTimeout) {
                case FAIL_PIPELINE -> {
                    future.completeExceptionally(te);
                    throw new SideComputeTimeoutException(key, timeout, te);
                }
                case USE_FALLBACK -> {
                    Object fb = fallback != null ? fallback.get() : null;
                    future.complete(fb);
                    execCtx.getContext().put(SideComputeKeys.valueKey(key), fb);
                }
                case IGNORE -> {
                    // Ne rien faire : pas de valeur résolue, le param verra que rien n'est là.
                }
            }
        } catch (Exception ex) {
            // Autres problèmes sur le future : on laisse remonter ou on wrappe
            throw new SideComputeExecutionException(key, ex);
        }
    }

    @Override
    public void afterExecution(Object result, StationExecutionContext context) {
        // Rien ici – la valeur est dans le context global, dispo pour les paramètres.
    }
}
