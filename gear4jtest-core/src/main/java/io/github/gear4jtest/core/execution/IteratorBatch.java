package io.github.gear4jtest.core.execution;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.List;

import io.github.gear4jtest.core.persistence.OperationExecutionRecord;

public final class IteratorBatch {
    private final UUID pipelineExecutionId;
    private final String iteratorId;
    private final long startIndexInclusive;
    private final long endIndexInclusive;
    private final Instant startedAt;
    private final Instant endedAt;
    private final Map<String, Long> operationCounts;
    private final List<OperationExecutionRecord> samples;
    private final String summaryJson;

    public IteratorBatch(UUID pipelineExecutionId, String iteratorId,
                         long startIndexInclusive, long endIndexInclusive,
                         Instant startedAt, Instant endedAt,
                         Map<String, Long> operationCounts,
                         List<OperationExecutionRecord> samples,
                         String summaryJson) {
        this.pipelineExecutionId = pipelineExecutionId;
        this.iteratorId = iteratorId;
        this.startIndexInclusive = startIndexInclusive;
        this.endIndexInclusive = endIndexInclusive;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.operationCounts = operationCounts;
        this.samples = samples;
        this.summaryJson = summaryJson;
    }
    public UUID getPipelineExecutionId(){return pipelineExecutionId;}
    public String getIteratorId(){return iteratorId;}
    public long getStartIndexInclusive(){return startIndexInclusive;}
    public long getEndIndexInclusive(){return endIndexInclusive;}
    public Instant getStartedAt(){return startedAt;}
    public Instant getEndedAt(){return endedAt;}
    public Map<String, Long> getOperationCounts(){return operationCounts;}
    public List<OperationExecutionRecord> getSamples(){return samples;}
    public String getSummaryJson(){return summaryJson;}
}
