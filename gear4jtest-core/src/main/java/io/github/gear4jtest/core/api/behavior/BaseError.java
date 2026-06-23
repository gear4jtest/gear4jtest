package io.github.gear4jtest.core.api.behavior;

public class BaseError<T> {
    private final Class<? extends Throwable> throwableType;
    private final SignalType signalType;
    private final Condition<T> condition;
    private final Runnable action;

    private BaseError(SignalType signalType,
                      Class<? extends Throwable> throwableType,
                      Condition<T> condition,
                      Runnable action) {
        this.signalType = signalType != null ? signalType : SignalType.FATAL;
        this.throwableType = throwableType;
        this.condition = condition;
        this.action = action;
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

    private abstract static class AbstractBuilder<T, SELF extends AbstractBuilder<T, SELF>> {
        private final SignalType signalType;
        private final Class<? extends Throwable> throwableType;
        private Condition<T> condition;
        private Runnable action;

        private AbstractBuilder(SignalType signalType, Class<? extends Throwable> throwableType) {
            this.signalType = signalType;
            this.throwableType = throwableType;
        }

        public SELF condition(Condition<T> condition) {
            this.condition = condition;
            return self();
        }

        public SELF action(Runnable action) {
            this.action = action;
            return self();
        }

        protected SignalType signalType() {
            return signalType;
        }

        protected Class<? extends Throwable> throwableType() {
            return throwableType;
        }

        protected Condition<T> condition() {
            return condition;
        }

        protected Runnable action() {
            return action;
        }

        protected abstract SELF self();
    }

    public static class SafeError<T> extends BaseError<T> {
        private SafeError(SignalType signalType,
                          Class<? extends Throwable> throwableType,
                          Condition<T> condition,
                          Runnable action) {
            super(signalType, throwableType, condition, action);
        }

        public static class Builder<T> extends AbstractBuilder<T, Builder<T>> {
            public Builder(SignalType signalType, Class<? extends Throwable> throwableType) {
                super(signalType, throwableType);
            }

            @Override
            protected Builder<T> self() {
                return this;
            }

            public SafeError<T> build() {
                return new SafeError<>(signalType(), throwableType(), condition(), action());
            }
        }
    }

    public static class UnSafeError<T> extends BaseError<T> {
        private UnSafeError(SignalType signalType,
                            Class<? extends Throwable> throwableType,
                            Condition<T> condition,
                            Runnable action) {
            super(signalType, throwableType, condition, action);
        }

        public static class Builder<T> extends AbstractBuilder<T, Builder<T>> {
            public Builder(SignalType signalType, Class<? extends Throwable> throwableType) {
                super(signalType, throwableType);
            }

            @Override
            protected Builder<T> self() {
                return this;
            }

            public UnSafeError<T> build() {
                return new UnSafeError<>(signalType(), throwableType(), condition(), action());
            }
        }
    }
}
