package io.github.gear4jtest.core.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default dependency-free UUIDv7 generator used by Gear4J.
 *
 * <p>
 * The implementation favors per-thread monotonic, time-ordered identifiers
 * without introducing an external dependency or a JVM-wide generation lock.
 * Users who need different throughput or generation semantics can provide their
 * own {@code IdGenerator}.
 * </p>
 */
public final class DefaultUuidGenerator {
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private DefaultUuidGenerator() {
    }

    public static UUID generate() {
        State state = STATE.get();
        long ts = System.currentTimeMillis();

        if (ts > state.lastTimestampMs) {
            state.lastTimestampMs = ts;
            state.counter = 0;
        } else {
            ts = state.lastTimestampMs;
            state.counter++;
            // Guard against sequence overflow within the same millisecond. The
            // wait is per-thread, avoiding the previous JVM-wide lock.
            if (state.counter > 0x0FFF) {
                while (ts <= state.lastTimestampMs) {
                    Thread.onSpinWait();
                    ts = System.currentTimeMillis();
                }
                state.lastTimestampMs = ts;
                state.counter = 0;
            }
        }

        long rnd = ThreadLocalRandom.current().nextLong();
        long msb = (ts << 16) | 0x7000L | (state.counter & 0x0FFFL);
        long lsb = (rnd & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }

    private static final class State {
        private long lastTimestampMs = -1L;
        private int counter;
    }
}
