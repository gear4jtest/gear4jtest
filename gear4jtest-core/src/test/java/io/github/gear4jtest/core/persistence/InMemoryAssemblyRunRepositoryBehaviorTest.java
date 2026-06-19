package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.model.StationLogStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAssemblyRunRepositoryBehaviorTest {
    @Test
    void saveOperationRecords_shouldStoreRootAndChildLogsInDeterministicPages() {
        // Given
        InMemoryAssemblyRunRepository repo = new InMemoryAssemblyRunRepository();
        UUID runId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        StationLogRecord firstRoot = log(runId, UUID.randomUUID(), "root-a", null, 10);
        StationLogRecord secondRoot = log(runId, UUID.randomUUID(), "root-b", null, 20);
        StationLogRecord child = log(runId, UUID.randomUUID(), "child", parentId, 30);
        StationLogRecord parent = log(runId, parentId, "parent", null, 25);

        // When
        repo.saveOperationRecords(List.of(secondRoot, child, firstRoot, parent));

        // Then
        assertThat(repo.findRootLogsByRunId(runId, PageRequest.first(10)))
                .extracting(StationLogRecord::operationId)
                .containsExactly("root-a", "root-b", "parent");
        assertThat(repo.findChildLogsByRunId(runId, parentId, PageRequest.first(10)))
                .extracting(StationLogRecord::operationId)
                .containsExactly("child");
        assertThat(repo.findAllLogsByRunId(runId, new PageRequest(1, 2)))
                .extracting(StationLogRecord::operationId)
                .containsExactly("root-b", "parent");
        assertThat(repo.countChildLogsByRunId(runId, parentId)).isEqualTo(1L);
        assertThat(repo.countChildLogsByRunId(UUID.randomUUID(), parentId)).isZero();
    }

    @Test
    void saveOperationRecord_shouldReplaceExistingLogById() {
        // Given
        InMemoryAssemblyRunRepository repo = new InMemoryAssemblyRunRepository();
        UUID runId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();

        // When
        repo.saveOperationRecord(log(runId, logId, "first", null, 10));
        repo.saveOperationRecord(log(runId, logId, "replacement", null, 20));

        // Then
        assertThat(repo.findAllLogsByRunId(runId, PageRequest.first(10)))
                .extracting(StationLogRecord::operationId)
                .containsExactly("replacement");
    }

    @Test
    void findRunQueries_shouldFilterPageAndDeleteRunsWithLogs() {
        // Given
        InMemoryAssemblyRunRepository repo = new InMemoryAssemblyRunRepository();
        AssemblyRunRecord first = run("pipe-a", ExecutionStatus.SUCCEEDED, 10);
        AssemblyRunRecord second = run("pipe-b", ExecutionStatus.FAILED, 20);
        AssemblyRunRecord third = run("pipe-a", ExecutionStatus.FAILED, 30);
        repo.save(first);
        repo.save(second);
        repo.save(third);
        repo.saveOperationRecord(log(first.id(), UUID.randomUUID(), "root", null, 10));

        // When / Then
        assertThat(repo.findByPipelineId("pipe-a", PageRequest.first(10)))
                .extracting(AssemblyRunRecord::id)
                .containsExactlyInAnyOrder(first.id(), third.id());
        assertThat(repo.findByStatus(ExecutionStatus.FAILED, PageRequest.first(10)))
                .extracting(AssemblyRunRecord::id)
                .containsExactlyInAnyOrder(second.id(), third.id());
        assertThat(repo.findAll(new PageRequest(1, 1))).hasSize(1);

        repo.delete(first.id());

        assertThat(repo.findById(first.id())).isEmpty();
        assertThat(repo.findAllLogsByRunId(first.id(), PageRequest.first(10))).isEmpty();
    }

    @Test
    void saveOperationRecords_shouldIgnoreNullAndEmptyBatches() {
        // Given
        InMemoryAssemblyRunRepository repo = new InMemoryAssemblyRunRepository();

        // When
        repo.saveOperationRecord(null);
        repo.saveOperationRecords(null);
        repo.saveOperationRecords(List.of());

        // Then
        assertThat(repo.findAllLogsByRunId(UUID.randomUUID(), PageRequest.first(10))).isEmpty();
    }

    private static AssemblyRunRecord run(String pipelineId, ExecutionStatus status, int second) {
        return new AssemblyRunRecord(UUID.randomUUID(), pipelineId, Map.of("pipeline", pipelineId), "input", "result",
                status, java.time.Instant.parse("2026-01-01T00:00:" + twoDigits(second) + "Z"),
                java.time.Instant.parse("2026-01-01T00:01:" + twoDigits(second) + "Z"), null, null, null, null);
    }

    private static StationLogRecord log(UUID runId, UUID logId, String operationId, UUID parentId, int second) {
        return new StationLogRecord(logId, runId, operationId, parentId, null, StationLogStatus.SUCCEEDED,
                java.time.Instant.parse("2026-01-01T00:00:" + twoDigits(second) + "Z"),
                java.time.Instant.parse("2026-01-01T00:01:" + twoDigits(second) + "Z"), null, null,
                Map.of("operation", operationId), null);
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
