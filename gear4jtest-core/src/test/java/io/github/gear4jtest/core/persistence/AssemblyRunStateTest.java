package io.github.gear4jtest.core.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.model.StationLogStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyRunStateTest {
    @Test
    void childOf_shouldLinkParentAndRootExecution() {
        UUID rootId = UUID.randomUUID();
        AssemblyRunTrace parent = new AssemblyRunTrace(UUID.randomUUID(), "parent", Map.of("k", "v"));
        parent.setRootExecutionId(rootId);

        AssemblyRunTrace child = AssemblyRunTrace.childOf(parent, "child");

        assertThat(child.getId()).isNotNull();
        assertThat(child.getAssemblyLineId()).isEqualTo("child");
        assertThat(child.getParentExecutionId()).isEqualTo(parent.getId());
        assertThat(child.getRootExecutionId()).isEqualTo(rootId);
        assertThat(child.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    }

    @Test
    void childOf_shouldUseParentAsRootWhenParentHasNoRoot() {
        AssemblyRunTrace parent = new AssemblyRunTrace(UUID.randomUUID(), "parent", Map.of());

        AssemblyRunTrace child = AssemblyRunTrace.childOf(parent, "child");

        assertThat(child.getRootExecutionId()).isEqualTo(parent.getId());
    }

    @Test
    void assemblyRunView_shouldDefensivelyCopyRootOperations() {
        AssemblyRunRecord summary = new AssemblyRunRecord(UUID.randomUUID(), "pipeline", Map.of(), null, null,
                ExecutionStatus.RUNNING, null, null, null, null, null, null);
        StationLogRecord log = new StationLogRecord(UUID.randomUUID(), summary.id(), "op", null, null,
                StationLogStatus.RUNNING, java.time.Instant.now(), null, null, null, Map.of(), null);
        AssemblyRunView view = new AssemblyRunView(summary, List.of(log));

        assertThat(view.getSummary()).isSameAs(summary);
        assertThat(view.getRootOperations()).containsExactly(log);
        List<StationLogRecord> rootOperations = view.getRootOperations();

        assertThatThrownBy(() -> rootOperations.add(log))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new AssemblyRunView(summary, null).getRootOperations()).isEmpty();
    }

    @Test
    void assemblyRunView_shouldRejectNullSummary() {
        assertThatNullPointerException().isThrownBy(() -> new AssemblyRunView(null, List.of()))
                .withMessage("summary must not be null");
    }
}
