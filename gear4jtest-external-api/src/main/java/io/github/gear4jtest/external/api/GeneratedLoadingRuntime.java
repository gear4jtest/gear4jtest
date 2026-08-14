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
import io.github.gear4jtest.external.api.loader.ClassLoaderRegistry;
import io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Bounded executor, deadline and single-flight lifecycle for generated loads.
 */
final class GeneratedLoadingRuntime implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GeneratedLoadingRuntime.class);
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
        LoadFlight current = inFlight.get(internalLoaderId);
        if (current != null) {
            counters.recordCacheMiss();
            counters.recordSingleFlightJoin();
            return awaitLoad(internalLoaderId, current);
        }

        GeneratedAssemblyLine<?, ?> cached = operation.findCached();
        if (cached != null) {
            counters.recordCacheHit();
            return cached;
        }
        counters.recordCacheMiss();

        LoadFlight owned = new LoadFlight(System.nanoTime());
        current = inFlight.putIfAbsent(internalLoaderId, owned);
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
            } else if (!flight.isTerminal()) {
                completeExceptionally(internalLoaderId, flight,
                                      new IOException(
                                              "Generated assembly-line loading operation returned no result for "
                                                      + internalLoaderId),
                                      counters::recordLoadFailed);
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
        CompletionAttempt attempt = flight.claimSuccessBeforeDeadline(timeoutNanos);
        if (attempt == CompletionAttempt.COMPLETED) {
            counters.recordCacheHit();
            inFlight.remove(internalLoaderId, flight);
            flight.complete(cached);
        } else if (attempt == CompletionAttempt.EXPIRED) {
            timeout(internalLoaderId, flight);
        }
    }

    private void completeSuccessfully(String internalLoaderId, LoadFlight flight, LoadResult loaded) {
        RegistrationAttempt registration = flight.reserveRegistrationBeforeDeadline(timeoutNanos);
        if (registration == RegistrationAttempt.EXPIRED) {
            timeout(internalLoaderId, flight);
            return;
        }
        if (registration == RegistrationAttempt.ALREADY_COMPLETED) {
            return;
        }

        try {
            loaded.register().run(flight);
        } catch (RuntimeException | Error failure) {
            completeExceptionally(internalLoaderId, flight, failure, counters::recordLoadFailed);
            discardRegistration(internalLoaderId, flight, loaded, failure);
            return;
        }

        CompletionAttempt attempt = flight.claimRegistrationSuccessBeforeDeadline(timeoutNanos);
        if (attempt == CompletionAttempt.COMPLETED) {
            counters.recordLoadSucceeded();
            inFlight.remove(internalLoaderId, flight);
            flight.complete(loaded.instance());
            return;
        }
        if (attempt == CompletionAttempt.EXPIRED) {
            timeout(internalLoaderId, flight);
        }
        discardRegistration(internalLoaderId, flight, loaded, null);
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
        FailureClaim claim = flight.claimFailure();
        if (!claim.claimed()) {
            return false;
        }
        try {
            beforeCompletion.run();
        } catch (RuntimeException | Error completionFailure) {
            if (completionFailure != failure) {
                failure.addSuppressed(completionFailure);
            }
        } finally {
            if (!claim.registrationCleanupRequired()) {
                inFlight.remove(internalLoaderId, flight);
            }
            flight.completeExceptionally(failure);
        }
        return true;
    }

    private void discardRegistration(String internalLoaderId,
                                     LoadFlight flight,
                                     LoadResult loaded,
                                     Throwable primaryFailure) {
        Throwable cleanupFailure = null;
        try {
            loaded.discard().run();
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
            if (primaryFailure != null && primaryFailure != failure) {
                primaryFailure.addSuppressed(failure);
            }
        } finally {
            flight.registrationCleanupFinished();
            inFlight.remove(internalLoaderId, flight);
        }
        if (cleanupFailure != null) {
            LOGGER.warn("Generated classloader registration cleanup failed; the single-flight slot was released. "
                    + "internalLoaderId={}", internalLoaderId, cleanupFailure);
        }
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

    @FunctionalInterface
    interface RegistrationOperation {
        void run(ClassLoaderRegistry.RegistrationLease registrationLease);
    }

    record LoadResult(GeneratedAssemblyLine<?, ?> instance,
                      RegistrationOperation register,
                      Runnable discard) {
        LoadResult(GeneratedAssemblyLine<?, ?> instance, Runnable register) {
            this(instance, ignored -> register.run(), () -> {
            });
        }

        LoadResult(GeneratedAssemblyLine<?, ?> instance, Runnable register, Runnable discard) {
            this(instance, ignored -> register.run(), discard);
        }

        LoadResult(GeneratedAssemblyLine<?, ?> instance, RegistrationOperation register) {
            this(instance, register, () -> {
            });
        }

        LoadResult {
            requireNonNull(instance, "instance must not be null");
            requireNonNull(register, "register must not be null");
            requireNonNull(discard, "discard must not be null");
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
            if (flight.isTerminal()) {
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

        void recordPhaseStarted(GeneratedLoadingPhase phase) {
            counters.recordPhaseStarted(phase);
        }

        void recordPhaseFinished(GeneratedLoadingPhase phase, long durationNanos, Throwable failure) {
            counters.recordPhaseFinished(phase, durationNanos, failure);
        }
    }

    private enum CompletionAttempt {
        COMPLETED,
        EXPIRED,
        ALREADY_COMPLETED
    }

    private enum RegistrationAttempt {
        ACQUIRED,
        EXPIRED,
        ALREADY_COMPLETED
    }

    private enum FlightState {
        ACTIVE,
        REGISTERING,
        SUCCEEDED,
        FAILED
    }

    private record FailureClaim(boolean claimed, boolean registrationCleanupRequired) {}

    private static final class LoadFlight implements ClassLoaderRegistry.RegistrationLease {
        private final long createdNanos;
        private final CompletableFuture<GeneratedAssemblyLine<?, ?>> result = new CompletableFuture<>();
        private Future<?> loadingTask;
        private ScheduledFuture<?> timeoutTask;
        private boolean cancellationRequested;
        private FlightState state = FlightState.ACTIVE;
        private boolean registrationCleanupRequired;

        private LoadFlight(long createdNanos) {
            this.createdNanos = createdNanos;
        }

        long createdNanos() {
            return createdNanos;
        }

        CompletableFuture<GeneratedAssemblyLine<?, ?>> result() {
            return result;
        }

        synchronized boolean isTerminal() {
            return state == FlightState.SUCCEEDED || state == FlightState.FAILED;
        }

        @Override
        public synchronized boolean isPublished() {
            return state == FlightState.SUCCEEDED;
        }

        synchronized void setLoadingTask(Future<?> value) {
            loadingTask = value;
            if (cancellationRequested) {
                value.cancel(true);
            }
        }

        synchronized void setTimeoutTask(ScheduledFuture<?> value) {
            timeoutTask = value;
            if (isTerminal()) {
                value.cancel(false);
            }
        }

        synchronized CompletionAttempt claimSuccessBeforeDeadline(long timeoutNanos) {
            if (state != FlightState.ACTIVE) {
                return CompletionAttempt.ALREADY_COMPLETED;
            }
            if (deadlineExpired(timeoutNanos)) {
                return CompletionAttempt.EXPIRED;
            }
            state = FlightState.SUCCEEDED;
            return CompletionAttempt.COMPLETED;
        }

        synchronized RegistrationAttempt reserveRegistrationBeforeDeadline(long timeoutNanos) {
            if (state != FlightState.ACTIVE) {
                return RegistrationAttempt.ALREADY_COMPLETED;
            }
            if (deadlineExpired(timeoutNanos)) {
                return RegistrationAttempt.EXPIRED;
            }
            state = FlightState.REGISTERING;
            registrationCleanupRequired = true;
            return RegistrationAttempt.ACQUIRED;
        }

        synchronized CompletionAttempt claimRegistrationSuccessBeforeDeadline(long timeoutNanos) {
            if (state != FlightState.REGISTERING) {
                return CompletionAttempt.ALREADY_COMPLETED;
            }
            if (deadlineExpired(timeoutNanos)) {
                return CompletionAttempt.EXPIRED;
            }
            state = FlightState.SUCCEEDED;
            registrationCleanupRequired = false;
            return CompletionAttempt.COMPLETED;
        }

        synchronized FailureClaim claimFailure() {
            if (isTerminal()) {
                return new FailureClaim(false, registrationCleanupRequired);
            }
            state = FlightState.FAILED;
            return new FailureClaim(true, registrationCleanupRequired);
        }

        synchronized void registrationCleanupFinished() {
            registrationCleanupRequired = false;
        }

        void complete(GeneratedAssemblyLine<?, ?> value) {
            result.complete(value);
        }

        void completeExceptionally(Throwable failure) {
            result.completeExceptionally(failure);
        }

        private boolean deadlineExpired(long timeoutNanos) {
            long elapsedNanos = System.nanoTime() - createdNanos;
            return elapsedNanos > 0L && elapsedNanos >= timeoutNanos;
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
