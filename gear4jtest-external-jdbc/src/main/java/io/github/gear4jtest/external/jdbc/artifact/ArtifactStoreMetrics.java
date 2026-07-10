package io.github.gear4jtest.external.jdbc.artifact;

import java.util.concurrent.atomic.LongAdder;

import io.github.gear4jtest.external.api.artifact.ArtifactStoreStats;

final class ArtifactStoreMetrics {
    private final LongAdder writesCompleted = new LongAdder();
    private final LongAdder writeFailures = new LongAdder();
    private final LongAdder bytesWritten = new LongAdder();
    private final LongAdder writeDurationNanos = new LongAdder();
    private final LongAdder readStreamsOpened = new LongAdder();
    private final LongAdder readStreamsCompleted = new LongAdder();
    private final LongAdder readStreamsClosedEarly = new LongAdder();
    private final LongAdder readFailures = new LongAdder();
    private final LongAdder bytesRead = new LongAdder();
    private final LongAdder readDurationNanos = new LongAdder();

    void recordWriteCompleted(long size, long durationNanos) {
        writesCompleted.increment();
        bytesWritten.add(size);
        writeDurationNanos.add(durationNanos);
    }

    void recordWriteFailure(long durationNanos) {
        writeFailures.increment();
        writeDurationNanos.add(durationNanos);
    }

    void recordReadOpened() {
        readStreamsOpened.increment();
    }

    void recordReadOpenFailure(long durationNanos) {
        readFailures.increment();
        readDurationNanos.add(durationNanos);
    }

    void recordReadClosed(long size,
                          long durationNanos,
                          boolean completed,
                          boolean closedEarly,
                          boolean failure) {
        bytesRead.add(size);
        readDurationNanos.add(durationNanos);
        if (completed) {
            readStreamsCompleted.increment();
        }
        if (closedEarly) {
            readStreamsClosedEarly.increment();
        }
        if (failure) {
            readFailures.increment();
        }
    }

    ArtifactStoreStats snapshot() {
        return new ArtifactStoreStats(writesCompleted.sum(), writeFailures.sum(), bytesWritten.sum(),
                writeDurationNanos.sum(), readStreamsOpened.sum(), readStreamsCompleted.sum(),
                readStreamsClosedEarly.sum(), readFailures.sum(), bytesRead.sum(), readDurationNanos.sum());
    }

    static long elapsedSince(long startedNanos) {
        return Math.max(0L, System.nanoTime() - startedNanos);
    }
}
