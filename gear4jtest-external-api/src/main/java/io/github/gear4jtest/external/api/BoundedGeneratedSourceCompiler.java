package io.github.gear4jtest.external.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

import io.github.gear4jtest.external.api.compiler.GeneratedSourceCompiler;
import io.github.gear4jtest.external.api.exception.CompilationException;
import io.github.gear4jtest.external.api.exception.CompilationLimitExceededException;
import io.github.gear4jtest.external.api.exception.CompilationTimeoutException;

/**
 * Bounded single-flight cache and isolated runtime shared by publication
 * validation and runtime loading.
 */
final class BoundedGeneratedSourceCompiler implements GeneratedSourceCompiler, AutoCloseable {
    static final int DEFAULT_MAX_ENTRIES = 128;
    static final long DEFAULT_MAX_BYTECODE_BYTES = 16L * 1024L * 1024L;
    private static final Runnable NO_OPERATION = () -> {
    };
    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final GeneratedSourceCompiler delegate;
    private final int maxEntries;
    private final long maxBytecodeBytes;
    private final GeneratedCompilationConfiguration configuration;
    private final long timeoutNanos;
    private final ThreadPoolExecutor compilationExecutor;
    private final ScheduledThreadPoolExecutor timeoutExecutor;
    private final Map<CompilationKey, CachedCompilation> completed;
    private final Map<CompilationKey, CompilationFlight> inFlight = new ConcurrentHashMap<>();
    private final GeneratedCompilationCounters counters = new GeneratedCompilationCounters();
    private final AtomicBoolean closed = new AtomicBoolean();
    private long cachedBytecodeBytes;

    BoundedGeneratedSourceCompiler(GeneratedSourceCompiler delegate) {
        this(delegate, DEFAULT_MAX_ENTRIES, DEFAULT_MAX_BYTECODE_BYTES,
                GeneratedCompilationConfiguration.defaults());
    }

    BoundedGeneratedSourceCompiler(GeneratedSourceCompiler delegate, int maxEntries) {
        this(delegate, maxEntries, DEFAULT_MAX_BYTECODE_BYTES, GeneratedCompilationConfiguration.defaults());
    }

    BoundedGeneratedSourceCompiler(GeneratedSourceCompiler delegate, int maxEntries, long maxBytecodeBytes) {
        this(delegate, maxEntries, maxBytecodeBytes, GeneratedCompilationConfiguration.defaults());
    }

    BoundedGeneratedSourceCompiler(GeneratedSourceCompiler delegate,
                                   int maxEntries,
                                   long maxBytecodeBytes,
                                   GeneratedCompilationConfiguration configuration) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        if (maxBytecodeBytes <= 0L) {
            throw new IllegalArgumentException("maxBytecodeBytes must be > 0");
        }
        this.maxEntries = maxEntries;
        this.maxBytecodeBytes = maxBytecodeBytes;
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.timeoutNanos = toNanosSaturated(configuration.timeout());
        this.completed = new LinkedHashMap<>(16, 0.75f, true);
        this.compilationExecutor = newCompilationExecutor(configuration);
        this.timeoutExecutor = newTimeoutExecutor();
    }

    @Override
    public Map<String, byte[]> compile(String className, byte[] sourceCode) {
        requireOpen();
        requireSourceWithinLimit(sourceCode);
        CompilationKey key = CompilationKey.of(className, sourceCode);
        Map<String, byte[]> cached = findCached(key);
        if (cached != null) {
            counters.recordCacheHit();
            return copyClasses(cached);
        }
        counters.recordCacheMiss();

        CompilationFlight owned = new CompilationFlight(System.nanoTime());
        CompilationFlight current = inFlight.putIfAbsent(key, owned);
        if (current != null) {
            counters.recordSingleFlightJoin();
            return copyClasses(await(key, current));
        }

        Map<String, byte[]> cachedAfterOwnership = findCached(key);
        if (cachedAfterOwnership != null) {
            completeSuccessfully(key, owned, cachedAfterOwnership, NO_OPERATION);
            return copyClasses(cachedAfterOwnership);
        }

        submit(key, sourceCode.clone(), owned);
        return copyClasses(await(key, owned));
    }

    GeneratedCompilationStats snapshotStats() {
        int cachedEntries;
        long cachedBytes;
        synchronized (completed) {
            cachedEntries = completed.size();
            cachedBytes = cachedBytecodeBytes;
        }
        return counters.snapshot(cachedEntries, cachedBytes, inFlight.size(), compilationExecutor.getActiveCount(),
                                 compilationExecutor.getQueue().size(), closed.get());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        CompilationException shutdown = new CompilationException("Generated-source compilation runtime is closed");
        inFlight.forEach((key, flight) -> {
            completeExceptionally(key, flight, shutdown, () -> flight.cancel(compilationExecutor));
        });
        inFlight.clear();
        timeoutExecutor.shutdownNow();
        compilationExecutor.shutdownNow();
    }

    private void submit(CompilationKey key, byte[] sourceCode, CompilationFlight flight) {
        try {
            Future<?> task = compilationExecutor.submit(() -> compileOwned(key, sourceCode, flight));
            flight.setCompilationTask(task);
            ScheduledFuture<?> timeoutTask = timeoutExecutor.schedule(
                                                                      () -> timeout(key, flight), timeoutNanos,
                                                                      TimeUnit.NANOSECONDS);
            flight.setTimeoutTask(timeoutTask);
        } catch (RejectedExecutionException rejected) {
            CompilationException failure = new CompilationException(
                    "Generated-source compilation executor is saturated or closed for " + key.className(),
                    List.of(), rejected);
            completeExceptionally(key, flight, failure, () -> {
                counters.recordCompilationRejected();
                flight.cancel(compilationExecutor);
            });
        }
    }

    private void compileOwned(CompilationKey key, byte[] sourceCode, CompilationFlight flight) {
        long startedNanos = System.nanoTime();
        counters.recordCompilationStarted();
        try {
            Map<String, byte[]> compiled = copyClasses(delegate.compile(key.className(), sourceCode),
                                                       configuration.maxCompilationOutputBytes());
            completeSuccessfully(key, flight, compiled, () -> {
                cache(key, compiled);
                counters.recordCompilationSucceeded();
            });
        } catch (CompilationLimitExceededException failure) {
            completeExceptionally(key, flight, failure, counters::recordCompilationLimitRejected);
        } catch (RuntimeException | Error failure) {
            completeExceptionally(key, flight, failure, counters::recordCompilationFailed);
        } finally {
            counters.recordDuration(System.nanoTime() - startedNanos);
            flight.cancelTimeout();
        }
    }

    private void timeout(CompilationKey key, CompilationFlight flight) {
        CompilationTimeoutException failure = new CompilationTimeoutException(key.className(),
                configuration.timeout());
        completeExceptionally(key, flight, failure, () -> {
            counters.recordCompilationTimedOut();
            flight.cancelCompilation(compilationExecutor);
        });
    }

    private boolean completeSuccessfully(CompilationKey key,
                                         CompilationFlight flight,
                                         Map<String, byte[]> value,
                                         Runnable beforeCompletion) {
        return flight.completeSuccessfully(value, () -> {
            beforeCompletion.run();
            inFlight.remove(key, flight);
        });
    }

    private boolean completeExceptionally(CompilationKey key,
                                          CompilationFlight flight,
                                          Throwable failure,
                                          Runnable beforeCompletion) {
        return flight.completeExceptionally(failure, () -> {
            beforeCompletion.run();
            inFlight.remove(key, flight);
        });
    }

    private Map<String, byte[]> await(CompilationKey key, CompilationFlight flight) {
        try {
            long remainingNanos = remainingNanos(flight.createdNanos());
            if (remainingNanos == 0L) {
                timeout(key, flight);
                return completedResult(key, flight.result());
            }
            return flight.result().get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompilationException("Interrupted while waiting for generated-source compilation "
                    + key.className(), List.of(), interrupted);
        } catch (TimeoutException timeout) {
            timeout(key, flight);
            return completedResult(key, flight.result());
        } catch (ExecutionException failure) {
            return rethrow(key, failure.getCause());
        }
    }

    private static Map<String, byte[]> completedResult(CompilationKey key,
                                                       CompletableFuture<Map<String, byte[]>> result) {
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompilationException("Interrupted while waiting for generated-source compilation "
                    + key.className(), List.of(), interrupted);
        } catch (ExecutionException failure) {
            return rethrow(key, failure.getCause());
        }
    }

    private static Map<String, byte[]> rethrow(CompilationKey key, Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new CompilationException("Generated-source compilation failed for " + key.className(),
                List.of(), cause);
    }

    private Map<String, byte[]> findCached(CompilationKey key) {
        synchronized (completed) {
            CachedCompilation cached = completed.get(key);
            return cached == null ? null : cached.classes();
        }
    }

    private void cache(CompilationKey key, Map<String, byte[]> compiled) {
        long bytecodeBytes = bytecodeSize(compiled);
        if (bytecodeBytes > maxBytecodeBytes) {
            return;
        }
        synchronized (completed) {
            CachedCompilation previous = completed.put(key, new CachedCompilation(compiled, bytecodeBytes));
            if (previous != null) {
                cachedBytecodeBytes -= previous.bytecodeBytes();
            }
            cachedBytecodeBytes += bytecodeBytes;
            while (completed.size() > maxEntries || cachedBytecodeBytes > maxBytecodeBytes) {
                CompilationKey eldest = completed.keySet().iterator().next();
                CachedCompilation removed = completed.remove(eldest);
                cachedBytecodeBytes -= removed.bytecodeBytes();
            }
        }
    }

    private long remainingNanos(long createdNanos) {
        long elapsedNanos = System.nanoTime() - createdNanos;
        if (elapsedNanos <= 0L) {
            return timeoutNanos;
        }
        return elapsedNanos >= timeoutNanos ? 0L : timeoutNanos - elapsedNanos;
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new CompilationException("Generated-source compilation runtime is closed");
        }
    }

    private void requireSourceWithinLimit(byte[] sourceCode) {
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        if (sourceCode.length > configuration.maxGeneratedSourceBytes()) {
            counters.recordCompilationLimitRejected();
            throw new CompilationLimitExceededException("Generated source", sourceCode.length,
                    configuration.maxGeneratedSourceBytes());
        }
    }

    private static ThreadPoolExecutor newCompilationExecutor(GeneratedCompilationConfiguration configuration) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(configuration.maxConcurrentCompilations(),
                configuration.maxConcurrentCompilations(), 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(configuration.queueCapacity()), threadFactory("gear4j-compilation-"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ScheduledThreadPoolExecutor newTimeoutExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                threadFactory("gear4j-compilation-timeout-"));
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

    private static long bytecodeSize(Map<String, byte[]> compiled) {
        long size = 0L;
        for (byte[] bytes : compiled.values()) {
            size = Math.addExact(size, bytes.length);
        }
        return size;
    }

    private static Map<String, byte[]> copyClasses(Map<String, byte[]> classes) {
        return copyClasses(classes, Long.MAX_VALUE);
    }

    private static Map<String, byte[]> copyClasses(Map<String, byte[]> classes, long maxBytecodeBytes) {
        if (classes == null) {
            throw new CompilationException("Generated-source compiler returned null");
        }
        Map<String, byte[]> validated = new LinkedHashMap<>();
        long bytecodeBytes = 0L;
        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
            String name = Objects.requireNonNull(entry.getKey(), "compiled class name must not be null");
            byte[] bytes = Objects.requireNonNull(entry.getValue(), "compiled class bytes must not be null");
            bytecodeBytes = saturatedAdd(bytecodeBytes, bytes.length);
            if (bytecodeBytes > maxBytecodeBytes) {
                throw new CompilationLimitExceededException("Generated bytecode", bytecodeBytes, maxBytecodeBytes);
            }
            validated.put(name, bytes);
        }
        Map<String, byte[]> copy = new LinkedHashMap<>();
        validated.forEach((name, bytes) -> copy.put(name, bytes.clone()));
        return Map.copyOf(copy);
    }

    private static long saturatedAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private record CachedCompilation(Map<String, byte[]> classes, long bytecodeBytes) {}

    private static final class CompilationFlight {
        private final long createdNanos;
        private final CompletableFuture<Map<String, byte[]>> result = new CompletableFuture<>();
        private Future<?> compilationTask;
        private ScheduledFuture<?> timeoutTask;
        private boolean cancellationRequested;

        private CompilationFlight(long createdNanos) {
            this.createdNanos = createdNanos;
        }

        long createdNanos() {
            return createdNanos;
        }

        CompletableFuture<Map<String, byte[]>> result() {
            return result;
        }

        synchronized void setCompilationTask(Future<?> value) {
            compilationTask = value;
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

        synchronized boolean completeSuccessfully(Map<String, byte[]> value, Runnable beforeCompletion) {
            if (result.isDone()) {
                return false;
            }
            beforeCompletion.run();
            result.complete(value);
            return true;
        }

        synchronized boolean completeExceptionally(Throwable failure, Runnable beforeCompletion) {
            if (result.isDone()) {
                return false;
            }
            beforeCompletion.run();
            result.completeExceptionally(failure);
            return true;
        }

        synchronized void cancelCompilation(ThreadPoolExecutor executor) {
            cancellationRequested = true;
            if (compilationTask != null) {
                compilationTask.cancel(true);
                if (compilationTask instanceof Runnable queuedTask) {
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
            cancelCompilation(executor);
            cancelTimeout();
        }
    }

    private record CompilationKey(String className, byte[] sourceHash) {
        private CompilationKey {
            Objects.requireNonNull(className, "className must not be null");
            sourceHash = sourceHash.clone();
        }

        static CompilationKey of(String className, byte[] sourceCode) {
            Objects.requireNonNull(sourceCode, "sourceCode must not be null");
            try {
                return new CompilationKey(className, MessageDigest.getInstance("SHA-256").digest(sourceCode));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("SHA-256 is not available", impossible);
            }
        }

        @Override
        public byte[] sourceHash() {
            return sourceHash.clone();
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof CompilationKey other
                    && className.equals(other.className)
                    && Arrays.equals(sourceHash, other.sourceHash);
        }

        @Override
        public int hashCode() {
            return 31 * className.hashCode() + Arrays.hashCode(sourceHash);
        }
    }
}
