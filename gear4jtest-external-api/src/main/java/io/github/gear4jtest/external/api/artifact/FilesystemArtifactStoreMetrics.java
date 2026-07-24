package io.github.gear4jtest.external.api.artifact;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

final class FilesystemArtifactStoreMetrics {
    private final LongAdder writesCompleted = new LongAdder();
    private final LongAdder writeFailures = new LongAdder();
    private final LongAdder bytesWritten = new LongAdder();
    private final LongAdder writeDurationNanos = new LongAdder();
    private final LongAdder readStreamsOpened = new LongAdder();
    private final LongAdder readStreamsCompleted = new LongAdder();
    private final LongAdder readFailures = new LongAdder();
    private final LongAdder bytesRead = new LongAdder();
    private final LongAdder readDurationNanos = new LongAdder();
    private final AtomicLong cleanupFailures = new AtomicLong();

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

    void recordReadCompleted(long size, long durationNanos) {
        readStreamsCompleted.increment();
        bytesRead.add(size);
        readDurationNanos.add(durationNanos);
    }

    void recordReadFailure(long durationNanos) {
        readFailures.increment();
        readDurationNanos.add(durationNanos);
    }

    long recordCleanupFailure() {
        return cleanupFailures.incrementAndGet();
    }

    ArtifactStoreStats snapshot() {
        return new ArtifactStoreStats(writesCompleted.sum(), writeFailures.sum(), bytesWritten.sum(),
                writeDurationNanos.sum(), readStreamsOpened.sum(), readStreamsCompleted.sum(), 0L,
                readFailures.sum(), bytesRead.sum(), readDurationNanos.sum(), cleanupFailures.get());
    }

    static long elapsedSince(long startedNanos) {
        return Math.max(0L, System.nanoTime() - startedNanos);
    }
}
