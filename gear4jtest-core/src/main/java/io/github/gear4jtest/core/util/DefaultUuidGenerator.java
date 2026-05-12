package io.github.gear4jtest.core.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Générateur UUID v7 par défaut pour Gear4j. * Performance : ~4 Millions
 * IDs/sec (limité par synchronized). C'est largement suffisant pour saturer
 * n'importe quelle base de données relationnelle. * Si un utilisateur a besoin
 * de plus, il doit implémenter l'interface IdGenerator et utiliser une
 * librairie spécialisée (ex: uuid-creator).
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
                // Protection overflow (4096 IDs/ms)
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
