package io.github.gear4jtest.core.model;

import java.util.Map;
import java.util.UUID;

import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionContextTest {
    @Test
    void putAndGet_shouldStoreTypedValuesInRunContext() {
        // Given
        ExecutionContext context = newContext();

        // When
        context.put("tenant", "tenant-a");
        context.put("attempt", 2);

        // Then
        assertThat(context.get("tenant", String.class)).isEqualTo("tenant-a");
        assertThat(context.get("attempt", Integer.class)).isEqualTo(2);
        assertThat(context.getContext()).containsEntry("tenant", "tenant-a");
    }

    @Test
    void find_shouldReturnOptionalTypedValueWithoutConflatingAbsenceAndWrongType() {
        // Given
        ExecutionContext context = newContext();
        context.put("tenant", "tenant-a");

        // When / Then
        assertThat(context.find("tenant", String.class)).contains("tenant-a");
        assertThat(context.find("missing", String.class)).isEmpty();
        assertThat(context.find("tenant", Integer.class)).isEmpty();
    }

    @Test
    void snapshotContext_shouldReturnImmutablePointInTimeCopy() {
        // Given
        ExecutionContext context = newContext();
        context.put("tenant", "tenant-a");
        Map<String, Object> snapshot = context.snapshotContext();

        // When
        context.put("tenant", "tenant-b");
        context.put("new", "value");

        // Then
        assertThat(snapshot).containsEntry("tenant", "tenant-a").doesNotContainKey("new");
        assertThatThrownBy(() -> snapshot.put("other", "value")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void currentItemId_shouldBeThreadLocalAndClearable() {
        // Given
        ExecutionContext context = newContext();

        // When
        context.setCurrentItemId("item-1");

        // Then
        assertThat(context.getCurrentItemId()).isEqualTo("item-1");

        // When
        context.setCurrentItemId(null);

        // Then
        assertThat(context.getCurrentItemId()).isNull();
    }

    @Test
    void enterItem_shouldRestorePreviousItemId() {
        // Given
        ExecutionContext context = newContext();
        context.setCurrentItemId("outer-item");

        // When
        try (var ignored = context.enterItem("inner-item")) {
            assertThat(context.getCurrentItemId()).isEqualTo("inner-item");
        }

        // Then
        assertThat(context.getCurrentItemId()).isEqualTo("outer-item");
    }

    @Test
    void enterBranch_shouldRestorePreviousBranchId() {
        // Given
        ExecutionContext context = newContext();

        // When
        try (var outer = context.enterBranch("outer-branch")) {
            assertThat(context.getCurrentBranchId()).isEqualTo("outer-branch");
            try (var inner = context.enterBranch("inner-branch")) {
                assertThat(context.getCurrentBranchId()).isEqualTo("inner-branch");
            }
            assertThat(context.getCurrentBranchId()).isEqualTo("outer-branch");
        }

        // Then
        assertThat(context.getCurrentBranchId()).isNull();
    }

    @Test
    void parentOperationStack_shouldExposeCurrentParentId() {
        // Given
        ExecutionContext context = newContext();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        // When / Then
        assertThat(context.getCurrentParentOperationId()).isNull();

        context.pushParentOperationId(first);
        assertThat(context.getCurrentParentOperationId()).isEqualTo(first);

        context.pushParentOperationId(second);
        assertThat(context.getCurrentParentOperationId()).isEqualTo(second);

        context.popParentOperationId();
        assertThat(context.getCurrentParentOperationId()).isEqualTo(first);

        context.popParentOperationId();
        assertThat(context.getCurrentParentOperationId()).isNull();
    }

    @Test
    void executionContext_shouldNotExposePublicConstructors() {
        // When / Then
        assertThat(ExecutionContext.class.getConstructors()).isEmpty();
    }

    @Test
    void builder_shouldCreateContextWithoutTelescopingConstructor() {
        // Given
        UUID executionId = UUID.randomUUID();
        var services = new ExecutionServices(null, noResources());
        var trace = new AssemblyRunTrace(executionId, "pipeline-1", Map.of());

        // When
        ExecutionContext context = ExecutionContext.builder()
                .executionId(executionId)
                .assemblyLineId("pipeline-1")
                .services(services)
                .assemblyRun(trace)
                .build();

        // Then
        assertThat(context.getExecutionId()).isEqualTo(executionId);
        assertThat(context.getAssemblyLineId()).isEqualTo("pipeline-1");
        assertThat(context.getServices()).isSameAs(services);
        assertThat(context.getAssemblyLineExecution()).isSameAs(trace);
    }

    private static ExecutionContext newContext() {
        return ExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .assemblyLineId("pipeline-1")
                .services(new ExecutionServices(null, noResources()))
                .assemblyRun(new AssemblyRunTrace(UUID.randomUUID(), "pipeline-1", Map.of()))
                .build();
    }

    private static ResourceFactory noResources() {
        return new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> clazz) {
                return null;
            }
        };
    }
}
