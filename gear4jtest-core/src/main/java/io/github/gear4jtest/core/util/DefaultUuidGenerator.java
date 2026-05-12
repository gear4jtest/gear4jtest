package io.github.gear4jtest.core.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default dependency-free UUIDv7 generator used by Gear4J.
 *
 * <p>
 * The implementation favors monotonic, time-ordered identifiers without
 * introducing an external dependency. Users who need different throughput or
 * generation semantics can provide their own {@code IdGenerator}.
 * </p>
 */
public final class DefaultUuidGenerator {

    private static final Object LOCK = new Object();
    private static long lastTimestampMs = -1L;
    private static int counter = 0;

    private DefaultUuidGenerator() {
    }

    public static UUID generate() {
        long ts = System.currentTimeMillis();
        long seq;

        synchronized (LOCK) {
            if (ts > lastTimestampMs) {
                lastTimestampMs = ts;
                counter = 0;
            } else {
                ts = lastTimestampMs;
                counter++;
                // Guard against sequence overflow within the same millisecond.
                if (counter > 0x0FFF) {
                    while (ts <= lastTimestampMs) {
                        Thread.onSpinWait(); // Java 9+ hint
                        ts = System.currentTimeMillis();
                    }
                    lastTimestampMs = ts;
                    counter = 0;
                }
            }
            seq = counter;
        }

        long rnd = ThreadLocalRandom.current().nextLong();
        long msb = (ts << 16) | 0x7000L | (seq & 0x0FFFL);
        long lsb = (rnd & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }
}
