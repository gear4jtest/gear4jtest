package io.github.gear4jtest.jdbc.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import io.github.gear4jtest.core.model.StationLogStatus;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@BenchmarkMode({ Mode.Throughput, Mode.AverageTime })
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class JdbcBatchBufferBenchmark {
    private static final int BATCH_SIZE = 256;
    private final List<StationLogRecord> records = records();
    private final PersistenceRuntimeCounters counters = new PersistenceRuntimeCounters();
    private OperationRecordBuffer buffer;

    @Setup(Level.Invocation)
    public void resetBuffer() {
        buffer = new OperationRecordBuffer(UUID.randomUUID(), BATCH_SIZE);
    }

    @Benchmark
    public int appendDrainBatch() {
        buffer.appendAll(records, BATCH_SIZE, counters);
        List<StationLogRecord> drained = buffer.drainBatch(BATCH_SIZE);
        buffer.acknowledgeDrainedBatch(drained);
        return drained.size();
    }

    private static List<StationLogRecord> records() {
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        List<StationLogRecord> result = new ArrayList<>(BATCH_SIZE);
        for (int index = 0; index < BATCH_SIZE; index++) {
            result.add(new StationLogRecord(UUID.randomUUID(), runId, "operation-" + index, null,
                    StationLogStatus.SUCCEEDED, now, now.plusMillis(1), null, null, Map.of("index", index),
                    "item-" + index));
        }
        return List.copyOf(result);
    }
}
