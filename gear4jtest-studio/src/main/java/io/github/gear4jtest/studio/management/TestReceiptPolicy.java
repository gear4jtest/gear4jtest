package io.github.gear4jtest.studio.management;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounded process-local idempotence, independent of durable execution history.
 */
public record TestReceiptPolicy(int capacity, Duration terminalTtl) {
    public TestReceiptPolicy {
        Objects.requireNonNull(terminalTtl);
        if (capacity < 1 || capacity > 10000 || terminalTtl.isNegative() || terminalTtl.isZero()
                || terminalTtl.compareTo(Duration.ofDays(1)) > 0)
            throw new IllegalArgumentException("Invalid receipt retention");
    }

    public static TestReceiptPolicy defaults() {
        return new TestReceiptPolicy(128, Duration.ofMinutes(30));
    }
}
