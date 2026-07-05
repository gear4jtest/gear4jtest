package io.github.gear4jtest.core.api.context;

/**
 * Mutable parameter holder injected before an operator execution.
 *
 * <p>
 * A station parameter can keep its injected value across executions or reset to
 * its default value after each station execution, depending on its lifecycle
 * policy.
 * </p>
 */
public final class StationParameter<T> {
    private final LifecyclePolicy lifecyclePolicy;
    private final T defaultValue;
    private T value;

    private StationParameter(Builder<T> builder) {
        this.lifecyclePolicy = builder.lifecyclePolicy;
        this.defaultValue = builder.defaultValue;
        this.value = builder.defaultValue;
    }

    public static <T> Builder<T> newBuilder() {
        return new Builder<>();
    }

    public T getValue() {
        return value;
    }

    public void injectValue(T newValue) {
        this.value = newValue;
    }

    public void afterExecutionCleanup() {
        if (lifecyclePolicy == LifecyclePolicy.PER_EXECUTION) {
            this.value = defaultValue;
        }
    }

    public enum LifecyclePolicy {
        PERSISTENT, PER_EXECUTION
    }

    public static final class Builder<T> {
        private LifecyclePolicy lifecyclePolicy = LifecyclePolicy.PERSISTENT;
        private T defaultValue;

        private Builder() {
        }

        public Builder<T> lifecyclePolicy(LifecyclePolicy lifecyclePolicy) {
            this.lifecyclePolicy = lifecyclePolicy;
            return this;
        }

        public Builder<T> defaultValue(T defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public StationParameter<T> build() {
            return new StationParameter<>(this);
        }
    }
}
