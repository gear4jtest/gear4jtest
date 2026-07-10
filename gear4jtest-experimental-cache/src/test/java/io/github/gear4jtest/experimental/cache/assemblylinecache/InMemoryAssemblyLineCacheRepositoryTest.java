package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAssemblyLineCacheRepositoryTest {
    @Test
    void save_shouldIsolateMutableValuesOnWriteAndEveryRead() {
        // Given
        InMemoryAssemblyLineCacheRepository repository = new InMemoryAssemblyLineCacheRepository(4,
                mutableListCloner());
        AssemblyLineCacheKey key = key(1);
        List<String> source = new ArrayList<>(List.of("original"));

        // When
        repository.save(entry(key, source, Instant.now().plusSeconds(60)));
        source.add("source-mutation");
        List<String> firstRead = repository.<List<String>>findValid(key, Instant.now()).orElseThrow().output();
        firstRead.add("reader-mutation");
        List<String> secondRead = repository.<List<String>>findValid(key, Instant.now()).orElseThrow().output();

        // Then
        assertThat(firstRead).containsExactly("original", "reader-mutation");
        assertThat(secondRead).containsExactly("original");
        assertThat(secondRead).isNotSameAs(firstRead).isNotSameAs(source);
        assertThat(repository.snapshotStats().hits()).isEqualTo(2);
    }

    @Test
    void save_shouldEvictLeastRecentlyUsedEntryWhenCapacityIsExceeded() {
        // Given
        InMemoryAssemblyLineCacheRepository repository = new InMemoryAssemblyLineCacheRepository(2,
                PayloadCloners.immutableAware());
        Instant expiry = Instant.now().plusSeconds(60);
        AssemblyLineCacheKey first = key(1);
        AssemblyLineCacheKey second = key(2);
        AssemblyLineCacheKey third = key(3);
        repository.save(entry(first, "first", expiry));
        repository.save(entry(second, "second", expiry));
        repository.findValid(first, Instant.now());

        // When
        repository.save(entry(third, "third", expiry));

        // Then
        assertThat(repository.findValid(first, Instant.now())).isPresent();
        assertThat(repository.findValid(second, Instant.now())).isEmpty();
        assertThat(repository.findValid(third, Instant.now())).isPresent();
        assertThat(repository.snapshotStats().entryCount()).isEqualTo(2);
        assertThat(repository.snapshotStats().capacityEvictions()).isEqualTo(1);
    }

    @Test
    void cleanUp_shouldRemoveExpiredEntriesAndUpdateWeight() {
        // Given
        InMemoryAssemblyLineCacheRepository repository = new InMemoryAssemblyLineCacheRepository(4, 10,
                PayloadCloners.immutableAware(), (key, output) -> ((String) output).length());
        Instant now = Instant.now();
        repository.save(entry(key(2), "valid", now.plusSeconds(60)));
        repository.save(entry(key(1), "old", now.plusSeconds(10)));

        // When
        int removed = repository.cleanUp(now.plusSeconds(20));

        // Then
        assertThat(removed).isEqualTo(1);
        assertThat(repository.snapshotStats().entryCount()).isEqualTo(1);
        assertThat(repository.snapshotStats().estimatedWeight()).isEqualTo(5);
        assertThat(repository.snapshotStats().expiredEvictions()).isEqualTo(1);
    }

    @Test
    void save_shouldRejectValuesThatTheConfiguredClonerCannotIsolate() {
        // Given
        InMemoryAssemblyLineCacheRepository repository = new InMemoryAssemblyLineCacheRepository();
        AssemblyLineCacheKey key = key(1);

        // When
        repository.save(entry(key, new ArrayList<>(List.of("mutable")), Instant.now().plusSeconds(60)));

        // Then
        assertThat(repository.findValid(key, Instant.now())).isEmpty();
        assertThat(repository.snapshotStats().rejectedWrites()).isEqualTo(1);
    }

    @Test
    void concurrentWrites_shouldNeverExceedConfiguredBounds() throws Exception {
        // Given
        int maximumEntries = 32;
        InMemoryAssemblyLineCacheRepository repository = new InMemoryAssemblyLineCacheRepository(maximumEntries,
                PayloadCloners.immutableAware());
        var executor = Executors.newFixedThreadPool(8);

        try {
            // When
            for (int index = 0; index < 1_000; index++) {
                int value = index;
                executor.submit(() -> repository.save(entry(key(value), "value-" + value,
                                                            Instant.now().plusSeconds(60))));
            }
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

            // Then
            AssemblyLineCacheStats stats = repository.snapshotStats();
            assertThat(stats.entryCount()).isLessThanOrEqualTo(maximumEntries);
            assertThat(stats.estimatedWeight()).isLessThanOrEqualTo(maximumEntries);
            assertThat(stats.capacityEvictions()).isPositive();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recordLoadDuration_shouldExposeCumulativeAndMaximumLoadTime() {
        // Given
        InMemoryAssemblyLineCacheRepository repository = new InMemoryAssemblyLineCacheRepository();

        // When
        repository.recordLoadDuration(Duration.ofMillis(2));
        repository.recordLoadDuration(Duration.ofMillis(5));

        // Then
        AssemblyLineCacheStats stats = repository.snapshotStats();
        assertThat(stats.loadCount()).isEqualTo(2);
        assertThat(stats.totalLoadTimeNanos()).isEqualTo(Duration.ofMillis(7).toNanos());
        assertThat(stats.maximumLoadTimeNanos()).isEqualTo(Duration.ofMillis(5).toNanos());
    }

    private static AssemblyLineCacheKey key(int value) {
        byte fingerprint = (byte) value;
        return new AssemblyLineCacheKey("assembly", "1", new byte[] { fingerprint }, new byte[] { fingerprint });
    }

    private static <T> AssemblyLineCacheEntry<T> entry(AssemblyLineCacheKey key, T value, Instant expiresAt) {
        return new AssemblyLineCacheEntry<>(key, value, expiresAt, Instant.now());
    }

    private static PayloadCloner mutableListCloner() {
        return new PayloadCloner() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T clonePayload(T payload) {
                if (payload instanceof List<?> list) {
                    return (T) new ArrayList<>(list);
                }
                return PayloadCloners.immutableAware().clonePayload(payload);
            }
        };
    }
}
