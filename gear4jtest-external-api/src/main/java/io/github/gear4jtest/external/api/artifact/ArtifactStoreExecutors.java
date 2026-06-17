package io.github.gear4jtest.external.api.artifact;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared bounded executor for best-effort artifact fallback and healing writes.
 */
public final class ArtifactStoreExecutors {
    private static final int QUEUE_CAPACITY = 512;
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();
    private static final Executor DEFAULT_ASYNC_EXECUTOR = new ThreadPoolExecutor(1, 4, 30L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), daemonThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());

    private ArtifactStoreExecutors() {
    }

    public static Executor defaultAsyncExecutor() {
        return DEFAULT_ASYNC_EXECUTOR;
    }

    private static ThreadFactory daemonThreadFactory() {
        return task -> {
            Thread thread = new Thread(task, "gear4j-artifact-async-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
