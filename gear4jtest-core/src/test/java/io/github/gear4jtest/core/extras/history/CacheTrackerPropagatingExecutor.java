package io.github.gear4jtest.core.extras.history;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public final class CacheTrackerPropagatingExecutor extends AbstractExecutorService {
    private final ExecutorService delegate;

    public CacheTrackerPropagatingExecutor(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(Runnable command) {
        ExpirableDependencyTracker snapshot = CacheTrackerContext.get();
        delegate.execute(() -> {
            try (CacheTrackerScope ignored = CacheTrackerScope.open(snapshot)) {
                command.run();
            }
        });
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        ExpirableDependencyTracker snapshot = CacheTrackerContext.get();
        return delegate.submit(() -> {
            try (CacheTrackerScope ignored = CacheTrackerScope.open(snapshot)) {
                return task.call();
            }
        });
    }

    @Override
    public Future<?> submit(Runnable task) {
        ExpirableDependencyTracker snapshot = CacheTrackerContext.get();
        return delegate.submit(() -> {
            try (CacheTrackerScope ignored = CacheTrackerScope.open(snapshot)) {
                task.run();
            }
        });
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        ExpirableDependencyTracker snapshot = CacheTrackerContext.get();
        return delegate.submit(() -> {
            try (CacheTrackerScope ignored = CacheTrackerScope.open(snapshot)) {
                task.run();
            }
        }, result);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        ExpirableDependencyTracker snapshot = CacheTrackerContext.get();

        List<Callable<T>> wrapped = tasks.stream()
                .map(task -> (Callable<T>) () -> {
                    try (CacheTrackerScope ignored = CacheTrackerScope.open(snapshot)) {
                        return task.call();
                    }
                })
                .collect(Collectors.toList());

        return delegate.invokeAll(wrapped);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
