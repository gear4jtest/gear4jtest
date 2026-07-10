package io.github.gear4jtest.experimental.cache.assemblylinecache;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import io.github.gear4jtest.core.api.context.PayloadCloner;
import io.github.gear4jtest.core.api.context.PayloadCloners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe, bounded, access-ordered in-memory cache.
 *
 * <p>
 * Values are isolated both when written and when read. The no-argument
 * constructor uses the strict immutable-aware cloner; mutable values therefore
 * require an explicit {@link PayloadCloner}. A rejected value never turns a
 * successful assembly-line run into a failure: it is skipped and exposed in
 * {@link #snapshotStats()}.
 * </p>
 */
public final class InMemoryAssemblyLineCacheRepository implements AssemblyLineCacheRepository,
        AssemblyLineCacheMetrics {
    public static final int DEFAULT_MAXIMUM_ENTRIES = 1_024;
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryAssemblyLineCacheRepository.class);

    private final Object monitor = new Object();
    private final Map<AssemblyLineCacheKey, WeightedEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int maximumEntries;
    private final long maximumWeight;
    private final PayloadCloner payloadCloner;
    private final AssemblyLineCacheWeigher weigher;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();
    private final LongAdder writes = new LongAdder();
    private final LongAdder expiredEvictions = new LongAdder();
    private final LongAdder capacityEvictions = new LongAdder();
    private final LongAdder rejectedWrites = new LongAdder();
    private final LongAdder loadCount = new LongAdder();
    private final LongAdder totalLoadTimeNanos = new LongAdder();
    private final AtomicLong maximumLoadTimeNanos = new AtomicLong();
    /** Guarded by {@link #monitor}. */
    private long currentWeight;

    public InMemoryAssemblyLineCacheRepository() {
        this(DEFAULT_MAXIMUM_ENTRIES, DEFAULT_MAXIMUM_ENTRIES, PayloadCloners.immutableAware(),
                AssemblyLineCacheWeigher.entryCount());
    }

    public InMemoryAssemblyLineCacheRepository(int maximumEntries, PayloadCloner payloadCloner) {
        this(maximumEntries, maximumEntries, payloadCloner, AssemblyLineCacheWeigher.entryCount());
    }

    public InMemoryAssemblyLineCacheRepository(int maximumEntries,
                                               long maximumWeight,
                                               PayloadCloner payloadCloner,
                                               AssemblyLineCacheWeigher weigher) {
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be > 0");
        }
        if (maximumWeight <= 0) {
            throw new IllegalArgumentException("maximumWeight must be > 0");
        }
        this.maximumEntries = maximumEntries;
        this.maximumWeight = maximumWeight;
        this.payloadCloner = Objects.requireNonNull(payloadCloner, "payloadCloner");
        this.weigher = Objects.requireNonNull(weigher, "weigher");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <OUT> Optional<AssemblyLineCacheEntry<OUT>> findValid(AssemblyLineCacheKey key, Instant now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(now, "now");

        AssemblyLineCacheEntry<?> entry;
        synchronized (monitor) {
            WeightedEntry weightedEntry = entries.get(key);
            if (weightedEntry == null) {
                misses.increment();
                return Optional.empty();
            }
            entry = weightedEntry.entry();
            if (!entry.isValidAt(now)) {
                removeEntry(key, weightedEntry);
                expiredEvictions.increment();
                misses.increment();
                return Optional.empty();
            }
        }

        OUT isolatedOutput;
        try {
            isolatedOutput = payloadCloner.clonePayload((OUT) entry.output());
        } catch (RuntimeException isolationFailure) {
            synchronized (monitor) {
                WeightedEntry current = entries.get(key);
                if (current != null && current.entry() == entry) {
                    removeEntry(key, current);
                }
            }
            rejectedWrites.increment();
            misses.increment();
            LOGGER.warn("Evicting cache entry because its output could not be isolated. assemblyLineId={}, version={}",
                        key.assemblyLineId(), key.pipelineVersion(), isolationFailure);
            return Optional.empty();
        }

        hits.increment();
        return Optional.of(new AssemblyLineCacheEntry<>(entry.key(), isolatedOutput, entry.expiresAt(),
                entry.createdAt()));
    }

    @Override
    public <OUT> void save(AssemblyLineCacheEntry<OUT> entry) {
        Objects.requireNonNull(entry, "entry");

        OUT isolatedOutput;
        long weight;
        try {
            isolatedOutput = payloadCloner.clonePayload(entry.output());
            weight = weigher.weigh(entry.key(), isolatedOutput);
            if (weight <= 0) {
                throw new IllegalArgumentException("Cache entry weight must be > 0");
            }
        } catch (RuntimeException validationFailure) {
            rejectedWrites.increment();
            LOGGER.debug("Skipping cache entry because its output could not be isolated or weighed. assemblyLineId={}, version={}",
                         entry.key().assemblyLineId(), entry.key().pipelineVersion(), validationFailure);
            return;
        }

        if (weight > maximumWeight) {
            rejectedWrites.increment();
            LOGGER.debug("Skipping cache entry because its weight exceeds the configured maximum. weight={}, maximumWeight={}",
                         weight, maximumWeight);
            return;
        }

        AssemblyLineCacheEntry<OUT> isolatedEntry = new AssemblyLineCacheEntry<>(entry.key(), isolatedOutput,
                entry.expiresAt(), entry.createdAt());
        Instant now = Instant.now();
        if (!isolatedEntry.isValidAt(now)) {
            rejectedWrites.increment();
            return;
        }
        synchronized (monitor) {
            evictExpiredEntries(now);
            WeightedEntry previous = entries.put(entry.key(), new WeightedEntry(isolatedEntry, weight));
            if (previous != null) {
                currentWeight -= previous.weight();
            }
            currentWeight += weight;
            writes.increment();
            evictToCapacity();
        }
    }

    /** Removes all expired entries and returns the number removed. */
    public int cleanUp(Instant now) {
        Objects.requireNonNull(now, "now");
        synchronized (monitor) {
            return evictExpiredEntries(now);
        }
    }

    /** Removes every cached entry without resetting cumulative statistics. */
    public void invalidateAll() {
        synchronized (monitor) {
            entries.clear();
            currentWeight = 0;
        }
    }

    @Override
    public void recordLoadDuration(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        long nanos = Math.max(0L, duration.toNanos());
        loadCount.increment();
        totalLoadTimeNanos.add(nanos);
        maximumLoadTimeNanos.accumulateAndGet(nanos, Math::max);
    }

    @Override
    public AssemblyLineCacheStats snapshotStats() {
        synchronized (monitor) {
            return new AssemblyLineCacheStats(hits.sum(), misses.sum(), writes.sum(), expiredEvictions.sum(),
                    capacityEvictions.sum(), rejectedWrites.sum(), entries.size(), currentWeight, loadCount.sum(),
                    totalLoadTimeNanos.sum(), maximumLoadTimeNanos.get());
        }
    }

    private int evictExpiredEntries(Instant now) {
        int removed = 0;
        Iterator<Map.Entry<AssemblyLineCacheKey, WeightedEntry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            WeightedEntry weightedEntry = iterator.next().getValue();
            if (!weightedEntry.entry().isValidAt(now)) {
                iterator.remove();
                currentWeight -= weightedEntry.weight();
                expiredEvictions.increment();
                removed++;
            }
        }
        return removed;
    }

    private void evictToCapacity() {
        Iterator<Map.Entry<AssemblyLineCacheKey, WeightedEntry>> iterator = entries.entrySet().iterator();
        while ((entries.size() > maximumEntries || currentWeight > maximumWeight) && iterator.hasNext()) {
            WeightedEntry evicted = iterator.next().getValue();
            iterator.remove();
            currentWeight -= evicted.weight();
            capacityEvictions.increment();
        }
    }

    private void removeEntry(AssemblyLineCacheKey key, WeightedEntry entry) {
        if (entries.remove(key, entry)) {
            currentWeight -= entry.weight();
        }
    }

    private record WeightedEntry(AssemblyLineCacheEntry<?> entry, long weight) {}
}
