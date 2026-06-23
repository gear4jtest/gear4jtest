package io.github.gear4jtest.core.execution.trace;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyRunTraceTest {
    @Test
    void constructor_shouldInitializeRunState() {
        // Given
        UUID id = UUID.randomUUID();

        // When
        AssemblyRunTrace trace = new AssemblyRunTrace(id, "pipeline", Map.of("tenant", "acme"));

        // Then
        assertThat(trace.getId()).isEqualTo(id);
        assertThat(trace.getAssemblyLineId()).isEqualTo("pipeline");
        assertThat(trace.getInputParams()).isEqualTo(Map.of("tenant", "acme"));
    }

    @Test
    void childOf_shouldUseParentRootWhenPresentOtherwiseParentId() {
        // Given
        AssemblyRunTrace root = new AssemblyRunTrace(UUID.randomUUID(), "root", Map.of());
        AssemblyRunTrace child = AssemblyRunTrace.childOf(root, "child");

        // When
        AssemblyRunTrace grandChild = AssemblyRunTrace.childOf(child, "grand-child");

        // Then
        assertThat(child.getAssemblyLineId()).isEqualTo("child");
        assertThat(child.getParentExecutionId()).isEqualTo(root.getId());
        assertThat(child.getRootExecutionId()).isEqualTo(root.getId());
        assertThat(child.getStartTime()).isNotNull();
        assertThat(grandChild.getParentExecutionId()).isEqualTo(child.getId());
        assertThat(grandChild.getRootExecutionId()).isEqualTo(root.getId());
    }
}
