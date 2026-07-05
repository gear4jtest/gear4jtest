package io.github.gear4jtest.experimental.cache.history;

public final class CacheTrackerScope implements AutoCloseable {
    private final ExpirableDependencyTracker previous;

    private CacheTrackerScope(ExpirableDependencyTracker tracker) {
        this.previous = CacheTrackerContext.get();
        if (tracker != null) {
            CacheTrackerContext.set(tracker);
        } else {
            CacheTrackerContext.clear();
        }
    }

    public static CacheTrackerScope open(ExpirableDependencyTracker tracker) {
        return new CacheTrackerScope(tracker);
    }

    @Override
    public void close() {
        if (previous != null) {
            CacheTrackerContext.set(previous);
        } else {
            CacheTrackerContext.clear();
        }
    }
}
