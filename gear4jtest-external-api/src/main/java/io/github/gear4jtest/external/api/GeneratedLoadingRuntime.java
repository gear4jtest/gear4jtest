package io.github.gear4jtest.external.api;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.gear4jtest.external.api.exception.GeneratedAssemblyLineLoadTimeoutException;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;

import static java.util.Objects.requireNonNull;

/**
 * Bounded executor, deadline and single-flight lifecycle for generated loads.
 */
final class GeneratedLoadingRuntime implements AutoCloseable {
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final GeneratedLoadingConfiguration configuration;
    private final long timeoutNanos;
    private final ThreadPoolExecutor loadingExecutor;
    private final ScheduledThreadPoolExecutor timeoutExecutor;
    private final Map<String, LoadFlight> inFlight = new ConcurrentHashMap<>();
    private final GeneratedLoadingCounters counters = new GeneratedLoadingCounters();
    private final AtomicBoolean closed = new AtomicBoolean();

    GeneratedLoadingRuntime(GeneratedLoadingConfiguration configuration) {
        this.configuration = requireNonNull(configuration, "configuration must not be null");
        this.timeoutNanos = toNanosSaturated(configuration.timeout());
        this.loadingExecutor = newLoadingExecutor(configuration);
        this.timeoutExecutor = newTimeoutExecutor();
    }

    GeneratedAssemblyLine<?, ?> load(String internalLoaderId, LoadingOperation operation) throws IOException {
        requireOpen();
        GeneratedAssemblyLine<?, ?> cached = operation.findCached();
        if (cached != null) {
            counters.recordCacheHit();
            return cached;
        }
        counters.recordCacheMiss();

        LoadFlight owned = new LoadFlight(System.nanoTime());
        LoadFlight current = inFlight.putIfAbsent(internalLoaderId, owned);
        if (current != null) {
            counters.recordSingleFlightJoin();
            return awaitLoad(internalLoaderId, current);
        }

        submit(internalLoaderId, owned, operation);
        return awaitLoad(internalLoaderId, owned);
    }

    GeneratedLoadingStats snapshotStats() {
        return counters.snapshot(inFlight.size(), loadingExecutor.getActiveCount(),
                                 loadingExecutor.getQueue().size(), closed.get());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException shutdown = new IOException("Generated assembly-line loading runtime is closed");
        inFlight.forEach((internalLoaderId, flight) -> completeExceptionally(internalLoaderId, flight,
                                                                             shutdown,
                                                                             () -> flight.cancel(loadingExecutor)));
        inFlight.clear();
        timeoutExecutor.shutdownNow();
        loadingExecutor.shutdownNow();
    }

    private void submit(String internalLoaderId, LoadFlight flight, LoadingOperation operation) {
        try {
            Future<?> task = loadingExecutor.submit(() -> loadOwned(internalLoaderId, flight, operation));
            flight.setLoadingTask(task);
            long remainingNanos = remainingNanos(flight.createdNanos());
            if (remainingNanos == 0L) {
                timeout(internalLoaderId, flight);
                return;
            }
            ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(
                                                                      () -> timeout(internalLoaderId, flight),
                                                                      remainingNanos, TimeUnit.NANOSECONDS);
            flight.setTimeoutTask(timeoutTask);
        } catch (RejectedExecutionException rejected) {
            IOException failure = new IOException(
                    "Generated assembly-line loading executor is saturated or closed for " + internalLoaderId,
                    rejected);
            completeExceptionally(internalLoaderId, flight, failure, () -> {
                counters.recordLoadRejected();
                flight.cancel(loadingExecutor);
            });
        }
    }

    private void loadOwned(String internalLoaderId, LoadFlight flight, LoadingOperation operation) {
        long loadStartedNanos = System.nanoTime();
        try {
            GeneratedAssemblyLine<?, ?> cached = operation.findCached();
            if (cached != null) {
                completeCached(internalLoaderId, flight, cached);
                return;
            }

            counters.recordLoadStarted();
            LoadAttempt attempt = new LoadAttempt(internalLoaderId, flight);
            LoadResult loaded = operation.load(attempt);
            if (loaded != null) {
                completeSuccessfully(internalLoaderId, flight, loaded);
            }
        } catch (IOException | RuntimeException | Error failure) {
            completeExceptionally(internalLoaderId, flight, failure, counters::recordLoadFailed);
        } finally {
            counters.recordLoadDuration(System.nanoTime() - loadStartedNanos);
            flight.cancelTimeout();
        }
    }

    private void completeCached(String internalLoaderId,
                                LoadFlight flight,
                                GeneratedAssemblyLine<?, ?> cached) {
        CompletionAttempt attempt = flight.completeBeforeDeadline(timeoutNanos, cached, () -> {
            counters.recordCacheHit();
            inFlight.remove(internalLoaderId, flight);
        });
        if (attempt == CompletionAttempt.EXPIRED) {
            timeout(internalLoaderId, flight);
        }
    }

    private void completeSuccessfully(String internalLoaderId, LoadFlight flight, LoadResult loaded) {
        CompletionAttempt attempt;
        try {
            attempt = flight.completeBeforeDeadline(timeoutNanos, loaded.instance(), () -> {
                loaded.register().run();
                counters.recordLoadSucceeded();
                inFlight.remove(internalLoaderId, flight);
            });
        } catch (RuntimeException | Error failure) {
            completeExceptionally(internalLoaderId, flight, failure, counters::recordLoadFailed);
            return;
        }
        if (attempt == CompletionAttempt.EXPIRED) {
            timeout(internalLoaderId, flight);
        }
    }

    private void timeout(String internalLoaderId, LoadFlight flight) {
        var failure = new GeneratedAssemblyLineLoadTimeoutException(internalLoaderId, configuration.timeout());
        completeExceptionally(internalLoaderId, flight, failure, () -> {
            counters.recordLoadTimedOut();
            flight.cancelLoading(loadingExecutor);
        });
    }

    private boolean completeExceptionally(String internalLoaderId,
                                          LoadFlight flight,
                                          Throwable failure,
                                          Runnable beforeCompletion) {
        return flight.completeExceptionally(failure, () -> {
            beforeCompletion.run();
            inFlight.remove(internalLoaderId, flight);
        });
    }

    private GeneratedAssemblyLine<?, ?> awaitLoad(String internalLoaderId, LoadFlight flight) throws IOException {
        try {
            long remainingNanos = remainingNanos(flight.createdNanos());
            if (remainingNanos == 0L) {
                timeout(internalLoaderId, flight);
                return completedResult(internalLoaderId, flight.result());
            }
            return flight.result().get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for generated assembly line " + internalLoaderId,
                    interrupted);
        } catch (TimeoutException timeout) {
            timeout(internalLoaderId, flight);
            return completedResult(internalLoaderId, flight.result());
        } catch (ExecutionException failure) {
            return rethrow(internalLoaderId, failure.getCause());
        }
    }

    private static GeneratedAssemblyLine<?, ?> completedResult(
                                                               String internalLoaderId,
                                                               CompletableFuture<GeneratedAssemblyLine<?, ?>> result)
            throws IOException {
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for generated assembly line " + internalLoaderId,
                    interrupted);
        } catch (ExecutionException failure) {
            return rethrow(internalLoaderId, failure.getCause());
        }
    }

    private static GeneratedAssemblyLine<?, ?> rethrow(String internalLoaderId, Throwable failure) throws IOException {
        if (failure instanceof IOException exception) {
            throw exception;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IOException("Generated assembly-line loading failed for " + internalLoaderId, failure);
    }

    private long remainingNanos(long createdNanos) {
        long elapsedNanos = System.nanoTime() - createdNanos;
        if (elapsedNanos <= 0L) {
            return timeoutNanos;
        }
        return elapsedNanos >= timeoutNanos ? 0L : timeoutNanos - elapsedNanos;
    }

    private void requireOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("Generated assembly-line loading runtime is closed");
        }
    }

    private static ThreadPoolExecutor newLoadingExecutor(GeneratedLoadingConfiguration configuration) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(configuration.maxConcurrentLoads(),
                configuration.maxConcurrentLoads(), 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(configuration.queueCapacity()), threadFactory("gear4j-loading-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ScheduledThreadPoolExecutor newTimeoutExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                threadFactory("gear4j-loading-timeout-"));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static ThreadFactory threadFactory(String prefix) {
        return task -> {
            Thread thread = new Thread(task, prefix + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    @FunctionalInterface
    interface LoadingOperation {
        LoadResult load(LoadAttempt attempt) throws IOException;

        default GeneratedAssemblyLine<?, ?> findCached() {
            return null;
        }
    }

    record LoadResult(GeneratedAssemblyLine<?, ?> instance, Runnable register) {
        LoadResult {
            requireNonNull(instance, "instance must not be null");
            requireNonNull(register, "register must not be null");
        }
    }

    final class LoadAttempt {
        private final String internalLoaderId;
        private final LoadFlight flight;

        private LoadAttempt(String internalLoaderId, LoadFlight flight) {
            this.internalLoaderId = internalLoaderId;
            this.flight = flight;
        }

        boolean continueLoading() {
            if (flight.result().isDone()) {
                return false;
            }
            if (remainingNanos(flight.createdNanos()) == 0L) {
                timeout(internalLoaderId, flight);
                return false;
            }
            return true;
        }

        void recordArtifactReadDuration(long durationNanos) {
            counters.recordArtifactReadDuration(durationNanos);
        }

        void recordTranslationDuration(long durationNanos) {
            counters.recordTranslationDuration(durationNanos);
        }

        void recordCompilationDuration(long durationNanos) {
            counters.recordCompilationDuration(durationNanos);
        }

        void recordInstantiationDuration(long durationNanos) {
            counters.recordInstantiationDuration(durationNanos);
        }
    }

    private enum CompletionAttempt {
        COMPLETED,
        EXPIRED,
        ALREADY_COMPLETED
    }

    private static final class LoadFlight {
        private final long createdNanos;
        private final CompletableFuture<GeneratedAssemblyLine<?, ?>> result = new CompletableFuture<>();
        private Future<?> loadingTask;
        private ScheduledFuture<?> timeoutTask;
        private boolean cancellationRequested;

        private LoadFlight(long createdNanos) {
            this.createdNanos = createdNanos;
        }

        long createdNanos() {
            return createdNanos;
        }

        CompletableFuture<GeneratedAssemblyLine<?, ?>> result() {
            return result;
        }

        synchronized void setLoadingTask(Future<?> value) {
            loadingTask = value;
            if (cancellationRequested) {
                value.cancel(true);
            }
        }

        synchronized void setTimeoutTask(ScheduledFuture<?> value) {
            timeoutTask = value;
            if (result.isDone()) {
                value.cancel(false);
            }
        }

        synchronized CompletionAttempt completeBeforeDeadline(long timeoutNanos,
                                                              GeneratedAssemblyLine<?, ?> value,
                                                              Runnable beforeCompletion) {
            if (result.isDone()) {
                return CompletionAttempt.ALREADY_COMPLETED;
            }
            long elapsedNanos = System.nanoTime() - createdNanos;
            if (elapsedNanos > 0L && elapsedNanos >= timeoutNanos) {
                return CompletionAttempt.EXPIRED;
            }
            beforeCompletion.run();
            result.complete(value);
            return CompletionAttempt.COMPLETED;
        }

        synchronized boolean completeExceptionally(Throwable failure, Runnable beforeCompletion) {
            if (result.isDone()) {
                return false;
            }
            beforeCompletion.run();
            result.completeExceptionally(failure);
            return true;
        }

        synchronized void cancelLoading(ThreadPoolExecutor executor) {
            cancellationRequested = true;
            if (loadingTask != null) {
                loadingTask.cancel(true);
                if (loadingTask instanceof Runnable queuedTask) {
                    executor.remove(queuedTask);
                }
            }
        }

        synchronized void cancelTimeout() {
            if (timeoutTask != null) {
                timeoutTask.cancel(false);
            }
        }

        synchronized void cancel(ThreadPoolExecutor executor) {
            cancelLoading(executor);
            cancelTimeout();
        }
    }
}
