package io.github.gear4jtest.core.util;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultUuidGeneratorTest {
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
                    .allSatisfy(uuid -> assertThat(uuid.version()).isEqualTo(7));
        } finally {
            executor.shutdownNow();
        }
    }
}
