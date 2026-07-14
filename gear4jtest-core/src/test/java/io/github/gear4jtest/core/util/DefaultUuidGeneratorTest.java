package io.github.gear4jtest.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultUuidGeneratorTest {
    private static final int VALUES_PER_LOGICAL_MILLISECOND = 4_096;

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void generate_shouldProduceUniqueUuidV7ValuesUnderConcurrentLoad() throws Exception {
        // Given
        int threadCount = 16;
        int uuidsPerThread = 1_000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        Set<UUID> generated = ConcurrentHashMap.newKeySet(threadCount * uuidsPerThread);
        var executor = Executors.newFixedThreadPool(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                executor.execute(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < uuidsPerThread; j++) {
                            generated.add(DefaultUuidGenerator.generate());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // When
            start.countDown();

            // Then
            assertThat(done.await(5, TimeUnit.SECONDS)).as("all UUID producer threads complete").isTrue();
            assertThat(generated).as("all generated IDs are unique").hasSize(threadCount * uuidsPerThread);
            assertThat(generated)
                    .as("the generator still emits version-7 UUIDs")
                    .allSatisfy(uuid -> {
                        assertThat(uuid.version()).isEqualTo(7);
                        assertThat(uuid.variant()).isEqualTo(2);
                    });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void next_shouldAdvanceLogicalTimestampWithoutPollingFrozenClock() {
        // Given
        long frozenTimestampMs = 1_000L;
        AtomicInteger clockReads = new AtomicInteger();
        LongSupplier frozenClock = () -> {
            int readCount = clockReads.incrementAndGet();
            if (readCount > VALUES_PER_LOGICAL_MILLISECOND + 1) {
                throw new AssertionError("generator polled a frozen clock while waiting for time to advance");
            }
            return frozenTimestampMs;
        };
        DefaultUuidGenerator generator = new DefaultUuidGenerator(frozenClock);

        // When
        List<UUID> generated = generate(generator, VALUES_PER_LOGICAL_MILLISECOND + 1);

        // Then
        assertThat(clockReads).hasValue(VALUES_PER_LOGICAL_MILLISECOND + 1);
        assertThat(timestamp(generated.get(VALUES_PER_LOGICAL_MILLISECOND - 1))).isEqualTo(frozenTimestampMs);
        assertThat(sequence(generated.get(VALUES_PER_LOGICAL_MILLISECOND - 1))).isEqualTo(0x0FFF);
        assertThat(timestamp(generated.get(VALUES_PER_LOGICAL_MILLISECOND))).isEqualTo(frozenTimestampMs + 1);
        assertThat(sequence(generated.get(VALUES_PER_LOGICAL_MILLISECOND))).isZero();
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void next_shouldRemainBoundedAfterMultiSecondClockRollback() {
        // Given
        long initialTimestampMs = 10_000L;
        long rolledBackTimestampMs = 5_000L;
        AtomicInteger clockReads = new AtomicInteger();
        LongSupplier rolledBackClock = () -> {
            int readCount = clockReads.incrementAndGet();
            if (readCount > VALUES_PER_LOGICAL_MILLISECOND + 1) {
                throw new AssertionError("generator polled a rolled-back clock while waiting for time to advance");
            }
            return readCount == 1 ? initialTimestampMs : rolledBackTimestampMs;
        };
        DefaultUuidGenerator generator = new DefaultUuidGenerator(rolledBackClock);

        // When
        List<UUID> generated = generate(generator, VALUES_PER_LOGICAL_MILLISECOND + 1);

        // Then
        assertThat(clockReads).hasValue(VALUES_PER_LOGICAL_MILLISECOND + 1);
        assertThat(timestamp(generated.get(0))).isEqualTo(initialTimestampMs);
        assertThat(timestamp(generated.get(VALUES_PER_LOGICAL_MILLISECOND - 1))).isEqualTo(initialTimestampMs);
        assertThat(timestamp(generated.get(VALUES_PER_LOGICAL_MILLISECOND))).isEqualTo(initialTimestampMs + 1);
    }

    @Test
    void next_shouldResumeWallClockWhenItMovesPastLogicalTime() {
        // Given
        AtomicLong clock = new AtomicLong(1_000L);
        DefaultUuidGenerator generator = new DefaultUuidGenerator(clock::get);
        List<UUID> generated = generate(generator, VALUES_PER_LOGICAL_MILLISECOND + 1);
        assertThat(timestamp(generated.get(VALUES_PER_LOGICAL_MILLISECOND))).isEqualTo(1_001L);

        // When
        UUID equalLogicalTime = generator.next();
        clock.set(1_002L);
        UUID caughtUp = generator.next();

        // Then
        assertThat(timestamp(equalLogicalTime)).isEqualTo(1_001L);
        assertThat(sequence(equalLogicalTime)).isEqualTo(1);
        assertThat(timestamp(caughtUp)).isEqualTo(1_002L);
        assertThat(sequence(caughtUp)).isZero();
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void next_shouldRejectLogicalTimestampOverflow() {
        // Given
        long maximumTimestampMs = 0x0000FFFFFFFFFFFFL;
        DefaultUuidGenerator generator = new DefaultUuidGenerator(() -> maximumTimestampMs);
        generate(generator, VALUES_PER_LOGICAL_MILLISECOND);

        // When / Then
        assertThatThrownBy(generator::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timestamp range exhausted");
    }

    @Test
    void next_shouldRejectTimestampOutsideUuidV7Range() {
        // Given
        DefaultUuidGenerator negativeClock = new DefaultUuidGenerator(() -> -1L);
        DefaultUuidGenerator overflowClock = new DefaultUuidGenerator(() -> 0x0001000000000000L);

        // When / Then
        assertThatThrownBy(negativeClock::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside the UUIDv7 range");
        assertThatThrownBy(overflowClock::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside the UUIDv7 range");
    }

    private static List<UUID> generate(DefaultUuidGenerator generator, int count) {
        List<UUID> generated = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            generated.add(generator.next());
        }
        return generated;
    }

    private static long timestamp(UUID uuid) {
        return uuid.getMostSignificantBits() >>> 16;
    }

    private static int sequence(UUID uuid) {
        return (int) (uuid.getMostSignificantBits() & 0x0FFF);
    }
}
