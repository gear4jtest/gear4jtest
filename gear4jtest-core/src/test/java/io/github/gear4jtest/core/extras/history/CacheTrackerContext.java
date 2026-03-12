package io.github.gear4jtest.core.extras.history;

public final class CacheTrackerContext {

    private static final ThreadLocal<ExpirableDependencyTracker> TRACKER = new ThreadLocal<>();

    private CacheTrackerContext() {}

    public static void set(ExpirableDependencyTracker tracker) {
        TRACKER.set(tracker);
    }

    public static ExpirableDependencyTracker get() {
        return TRACKER.get();
    }

    public static void clear() {
        TRACKER.remove();
    }
}
