package io.github.gear4jtest.core.api.behavior;

public class BaseError<T> {
    protected Class<? extends Throwable> throwableType;
    protected SignalType signalType;
    protected Condition<T> condition;
    protected Runnable action;

    private BaseError() {
        this.signalType = SignalType.FATAL;
    }

    public Class<? extends Throwable> getThrowableType() {
        return throwableType;
    }

    public Condition<T> getCondition() {
        return condition;
    }

    public Runnable getAction() {
        return action;
    }

    public SignalType getSignalType() {
        return signalType;
    }

    public static class SafeError<T> extends BaseError<T> {
        public static class Builder<T> {
            protected final SafeError<T> managedInstance;

            public Builder(SignalType signalType, Class<? extends Throwable> throwableType) {
                managedInstance = new SafeError<>();
                managedInstance.signalType = signalType;
                managedInstance.throwableType = throwableType;
            }

            public Builder<T> condition(Condition<T> condition) {
                managedInstance.condition = condition;
                return this;
            }

            public Builder<T> action(Runnable action) {
                managedInstance.action = action;
                return this;
            }

            public SafeError<T> build() {
                return managedInstance;
            }
        }
    }

    public static class UnSafeError<T> extends BaseError<T> {
        public static class Builder<T> {
            protected final UnSafeError<T> managedInstance;

            public Builder(SignalType signalType, Class<? extends Throwable> throwableType) {
                managedInstance = new UnSafeError<>();
                managedInstance.signalType = signalType;
                managedInstance.throwableType = throwableType;
            }

            public Builder<T> condition(Condition<T> condition) {
                managedInstance.condition = condition;
                return this;
            }

            public Builder<T> action(Runnable action) {
                managedInstance.action = action;
                return this;
            }

            public UnSafeError<T> build() {
                return managedInstance;
            }
        }
    }
}
