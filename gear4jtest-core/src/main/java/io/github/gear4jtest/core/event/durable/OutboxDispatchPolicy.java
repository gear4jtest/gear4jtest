package io.github.gear4jtest.core.event.durable;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Retry/dead-letter policy used by {@link OutboxDispatcher}.
 *
 * <p>
 * The durable event SPI is intentionally small: stores own their locking and
 * persistence model, while this policy decides whether a failed dispatch should
 * be retried or considered terminal. Attempt counts are interpreted as total
 * dispatch attempts already performed after the current failure has happened.
 * </p>
 */
public final class OutboxDispatchPolicy {
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofMinutes(1);

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final Predicate<Throwable> retryableFailurePredicate;

    private OutboxDispatchPolicy(Builder builder) {
        this.maxAttempts = positive(builder.maxAttempts, "maxAttempts");
        this.initialBackoff = positive(builder.initialBackoff, "initialBackoff");
        this.maxBackoff = positive(builder.maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        }
        this.retryableFailurePredicate = Objects.requireNonNull(builder.retryableFailurePredicate,
                                                                "retryableFailurePredicate must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static OutboxDispatchPolicy defaults() {
        return builder().build();
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public boolean shouldRetry(Throwable failure, int attemptsAfterFailure) {
        Objects.requireNonNull(failure, "failure must not be null");
        return attemptsAfterFailure < maxAttempts && retryableFailurePredicate.test(failure);
    }

    public Duration retryDelay(int attemptsAfterFailure) {
        int exponent = Math.max(0, attemptsAfterFailure - 1);
        Duration delay = initialBackoff;
        for (int i = 0; i < exponent; i++) {
            if (delay.compareTo(maxBackoff.dividedBy(2)) >= 0) {
                return maxBackoff;
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    public static final class Builder {
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration initialBackoff = DEFAULT_INITIAL_BACKOFF;
        private Duration maxBackoff = DEFAULT_MAX_BACKOFF;
        private Predicate<Throwable> retryableFailurePredicate = failure -> true;

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder retryableFailurePredicate(Predicate<Throwable> retryableFailurePredicate) {
            this.retryableFailurePredicate = retryableFailurePredicate;
            return this;
        }

        public OutboxDispatchPolicy build() {
            return new OutboxDispatchPolicy(this);
        }
    }
}
