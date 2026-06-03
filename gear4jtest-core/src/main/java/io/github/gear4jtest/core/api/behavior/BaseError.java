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
            private final SignalType signalType;
            private final Class<? extends Throwable> throwableType;
            private Condition<T> condition;
            private Runnable action;

            public Builder(SignalType signalType, Class<? extends Throwable> throwableType) {
                this.signalType = signalType;
                this.throwableType = throwableType;
            }

            public Builder<T> condition(Condition<T> condition) {
                this.condition = condition;
                return this;
            }

            public Builder<T> action(Runnable action) {
                this.action = action;
                return this;
            }

            public SafeError<T> build() {
                SafeError<T> error = new SafeError<>();
                error.signalType = signalType;
                error.throwableType = throwableType;
                error.condition = condition;
                error.action = action;
                return error;
            }
        }
    }

    public static class UnSafeError<T> extends BaseError<T> {
        public static class Builder<T> {
            private final SignalType signalType;
            private final Class<? extends Throwable> throwableType;
            private Condition<T> condition;
            private Runnable action;

            public Builder(SignalType signalType, Class<? extends Throwable> throwableType) {
                this.signalType = signalType;
                this.throwableType = throwableType;
            }

            public Builder<T> condition(Condition<T> condition) {
                this.condition = condition;
                return this;
            }

            public Builder<T> action(Runnable action) {
                this.action = action;
                return this;
            }

            public UnSafeError<T> build() {
                UnSafeError<T> error = new UnSafeError<>();
                error.signalType = signalType;
                error.throwableType = throwableType;
                error.condition = condition;
                error.action = action;
                return error;
            }
        }
    }
}
