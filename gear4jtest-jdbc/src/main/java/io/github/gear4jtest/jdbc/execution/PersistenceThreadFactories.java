package io.github.gear4jtest.jdbc.execution;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Thread factories used by asynchronous JDBC persistence. */
final class PersistenceThreadFactories {
    private PersistenceThreadFactories() {
    }

    static ThreadFactory flushWorker() {
        return namedDaemonFactory("gear4j-db-flush-");
    }

    static ThreadFactory maintenance() {
        return namedDaemonFactory("gear4j-db-flush-timer-");
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
