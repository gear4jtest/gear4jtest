package io.github.gear4jtest.core.persistence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationLogSnapshotTest {
    @Test
    void from_shouldCreateImmutableSnapshotIncludingBranchAndContext() {
        UUID executionId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        StationLog log = StationLog.start(executionId, "station-a", parentId);
        log.setBranchId("branch-1");
        log.setContext(new LinkedHashMap<>(Map.of("secret", "value")));
        log.setItemId("item-1");
        log.markSuccess("result");

        StationLogSnapshot snapshot = StationLogSnapshot.from(log);

        assertThat(snapshot.id()).isEqualTo(log.getId());
        assertThat(snapshot.pipelineExecutionId()).isEqualTo(executionId);
        assertThat(snapshot.operationId()).isEqualTo("station-a");
        assertThat(snapshot.parentOperationId()).isEqualTo(parentId);
        assertThat(snapshot.branchId()).isEqualTo("branch-1");
        assertThat(snapshot.status()).isEqualTo(StationLog.Status.SUCCEEDED);
        assertThat(snapshot.context()).containsEntry("secret", "value");
        Map<String, Object> context = snapshot.context();

        assertThatThrownBy(() -> context.put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toStationLog_shouldCreateMutableCopyOfContext() {
        StationLogSnapshot snapshot = new StationLogSnapshot(UUID.randomUUID(), UUID.randomUUID(), "station-b",
                UUID.randomUUID(), "branch-2", StationLog.Status.FAILED, null, null, "boom", "handler",
                Map.of("k", "v"), "item-2");

        StationLog log = snapshot.toStationLog();

        assertThat(log.getId()).isEqualTo(snapshot.id());
        assertThat(log.getPipelineExecutionId()).isEqualTo(snapshot.pipelineExecutionId());
        assertThat(log.getOperationId()).isEqualTo(snapshot.operationId());
        assertThat(log.getParentOperationId()).isEqualTo(snapshot.parentOperationId());
        assertThat(log.getBranchId()).isEqualTo(snapshot.branchId());
        assertThat(log.getStatus()).isEqualTo(snapshot.status());
        assertThat(log.getErrorMessage()).isEqualTo("boom");
        assertThat(log.getErrorHandlerMessages()).isEqualTo("handler");
        assertThat(log.getItemId()).isEqualTo("item-2");
        log.getContext().put("other", "value");
        assertThat(snapshot.context()).doesNotContainKey("other");
    }

    @Test
    void from_shouldRejectNullLog() {
        assertThatThrownBy(() -> StationLogSnapshot.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("log must not be null");
    }
}
