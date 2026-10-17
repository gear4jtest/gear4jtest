package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregates of visible retained observations only; partial always identifies a
 * scan-limited result.
 */
public record RunMetrics(long total,
                         Map<String, Long> statuses,
                         Long minimumMillis,
                         Double averageMillis,
                         Long maximumMillis,
                         Long p95Millis,
                         List<Bucket> timeline,
                         List<Operation> operations,
                         boolean partial,
                         Instant from,
                         Instant until) {
    public RunMetrics {
        statuses = Map.copyOf(statuses);
        timeline = List.copyOf(timeline);
        operations = List.copyOf(operations);
    }

    public record Bucket(Instant from, long runs, long failures) {}

    public record Operation(String nodeId, long executions, long failures, Double averageMillis, Long maximumMillis) {}
}
