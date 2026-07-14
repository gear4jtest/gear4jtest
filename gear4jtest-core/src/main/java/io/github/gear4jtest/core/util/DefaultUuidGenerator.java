package io.github.gear4jtest.core.util;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/**
 * Default dependency-free UUIDv7 generator used by Gear4J.
 *
 * <p>
 * The implementation favors per-thread monotonic, time-ordered identifiers
 * without introducing an external dependency or a JVM-wide generation lock.
 * Users who need different throughput or generation semantics can provide their
 * own {@code IdGenerator}.
 * </p>
 *
 * <p>
 * When the wall clock does not advance, each thread uses the UUIDv7 12-bit
 * sequence. After all 4096 sequence values have been consumed, the generator
 * advances its logical timestamp by one millisecond instead of waiting for the
 * wall clock. This keeps generation bounded during clock rollback or a frozen
 * clock, at the cost of allowing the encoded timestamp to temporarily lead wall
 * time.
 * </p>
 */
public final class DefaultUuidGenerator {
    private static final int MAX_COUNTER = 0x0FFF;
    private static final long MAX_TIMESTAMP_MS = 0x0000FFFFFFFFFFFFL;
    private static final DefaultUuidGenerator DEFAULT = new DefaultUuidGenerator(System::currentTimeMillis);

    private final LongSupplier currentTimeMillis;
    private final ThreadLocal<State> state = ThreadLocal.withInitial(State::new);

    DefaultUuidGenerator(LongSupplier currentTimeMillis) {
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
    }

    public static UUID generate() {
        return DEFAULT.next();
    }

    UUID next() {
        State currentState = state.get();
        long wallClockTimestampMs = currentTimeMillis.getAsLong();
        validateTimestamp(wallClockTimestampMs);

        long timestampMs;
        if (wallClockTimestampMs > currentState.lastTimestampMs) {
            timestampMs = wallClockTimestampMs;
            currentState.lastTimestampMs = timestampMs;
            currentState.counter = 0;
        } else if (currentState.counter < MAX_COUNTER) {
            timestampMs = currentState.lastTimestampMs;
            currentState.counter++;
        } else {
            timestampMs = advanceLogicalTimestamp(currentState.lastTimestampMs);
            currentState.lastTimestampMs = timestampMs;
            currentState.counter = 0;
        }

        long randomBits = ThreadLocalRandom.current().nextLong();
        long mostSignificantBits = (timestampMs << 16) | 0x7000L | (currentState.counter & MAX_COUNTER);
        long leastSignificantBits = (randomBits & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private static long advanceLogicalTimestamp(long lastTimestampMs) {
        if (lastTimestampMs >= MAX_TIMESTAMP_MS) {
            throw new IllegalStateException("UUIDv7 timestamp range exhausted");
        }
        return lastTimestampMs + 1;
    }

    private static void validateTimestamp(long timestampMs) {
        if (timestampMs < 0 || timestampMs > MAX_TIMESTAMP_MS) {
            throw new IllegalStateException("Clock returned a timestamp outside the UUIDv7 range: " + timestampMs);
        }
    }

    private static final class State {
        private long lastTimestampMs = -1L;
        private int counter;
    }
}
