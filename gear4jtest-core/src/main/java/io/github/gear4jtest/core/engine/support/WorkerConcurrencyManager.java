package io.github.gear4jtest.core.engine.support;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorkerConcurrencyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerConcurrencyManager.class);
    private static final WorkerConcurrencyManager GLOBAL = new WorkerConcurrencyManager(
            WorkerConcurrencyRegistryConfiguration.defaults());

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final ConcurrentMap<WeakIdentityKey, WorkerConcurrencyGuard> guards = new ConcurrentHashMap<>();
    private final WorkerConcurrencyRegistryConfiguration configuration;
    private final AtomicLong acquisitions = new AtomicLong();
    private final AtomicLong lastWarningAtSize = new AtomicLong();

    public WorkerConcurrencyManager() {
        this(WorkerConcurrencyRegistryConfiguration.defaults());
    }

    public WorkerConcurrencyManager(WorkerConcurrencyRegistryConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Returns the process-wide manager used by the safe default policy.
     */
    public static WorkerConcurrencyManager global() {
        return GLOBAL;
    }

    public WorkerConcurrencyGuard guardFor(Object worker) {
        Objects.requireNonNull(worker, "worker must not be null");

        purgeCollectedWorkersIfNeeded();
        WorkerConcurrencyGuard guard = guards.computeIfAbsent(new WeakIdentityKey(worker, referenceQueue),
                                                              ignored -> new WorkerConcurrencyGuard());
        enforceSizeGuardrails();
        return guard;
    }

    public int trackedWorkerCount() {
        purgeCollectedWorkers();
        return guards.size();
    }

    /**
     * Releases all guard references, typically when a local manager is discarded.
     *
     * <p>
     * This method should not be called on the global manager while executions may
     * be in progress, because doing so can break the process-wide protection
     * contract.
     * </p>
     */
    public void clear() {
        guards.clear();
        drainReferenceQueue();
    }

    public void purgeCollectedWorkers() {
        drainReferenceQueue();
    }

    private void purgeCollectedWorkersIfNeeded() {
        long current = acquisitions.incrementAndGet();
        if (current % configuration.purgeEveryAcquisitions() == 0) {
            purgeCollectedWorkers();
        }
    }

    private void enforceSizeGuardrails() {
        int size = guards.size();
        if (size < configuration.warnWhenTrackedWorkersExceeds()) {
            return;
        }

        purgeCollectedWorkers();
        size = guards.size();

        if (size >= configuration.failWhenTrackedWorkersExceeds()) {
            throw new IllegalStateException("Too many worker instances tracked by WorkerConcurrencyManager: "
                    + size + ". This usually means prototype workers are created at high volume while global "
                    + "per-instance locking is enabled. Use WorkerConcurrencyPolicy.ALLOW_PARALLEL_INVOCATIONS "
                    + "for thread-safe workers, "
                    + "WorkerConcurrencyPolicy.LOCK_REUSED_WORKER_INSTANCE_ONLY for guaranteed prototype workers, "
                    + "or WorkerConcurrencyPolicy.ENGINE_LOCAL_LOCK_PER_WORKER_INSTANCE when process-wide protection "
                    + "is not required.");
        }

        long previousWarning = lastWarningAtSize.get();
        if (size > previousWarning && lastWarningAtSize.compareAndSet(previousWarning, size)) {
            LOGGER.warn("WorkerConcurrencyManager is tracking {} live worker instances. This may indicate high-volume "
                    + "prototype workers while per-instance locking is enabled.", size);
        }
    }

    @SuppressWarnings("unchecked")
    private void drainReferenceQueue() {
        Reference<? extends Object> reference;
        while ((reference = referenceQueue.poll()) != null) {
            guards.remove((WeakIdentityKey) reference);
        }
    }

    private static final class WeakIdentityKey extends WeakReference<Object> {
        private final int identityHashCode;

        private WeakIdentityKey(Object referent, ReferenceQueue<Object> referenceQueue) {
            super(Objects.requireNonNull(referent, "referent must not be null"), referenceQueue);
            this.identityHashCode = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHashCode;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WeakIdentityKey otherKey)) {
                return false;
            }

            Object referent = get();
            Object otherReferent = otherKey.get();
            return referent != null && referent == otherReferent;
        }
    }
}
