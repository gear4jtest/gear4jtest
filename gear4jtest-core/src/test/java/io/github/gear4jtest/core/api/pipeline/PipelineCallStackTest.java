package io.github.gear4jtest.core.api.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineCallStackTest {
    @Test
    void enter_shouldTrackNestedReferencesAndLeaveWhenScopeCloses() {
        // Given
        PipelineCallStack stack = PipelineCallStack.withMaxDepth(3);
        PipelineReference root = reference("root");
        PipelineReference child = reference("child");

        // When / Then
        try (PipelineCallStack.Scope ignoredRoot = stack.enter(root)) {
            assertThat(stack.snapshot()).containsExactly(root);

            try (PipelineCallStack.Scope ignoredChild = stack.enter(child)) {
                assertThat(stack.snapshot()).containsExactly(child, root);
            }

            assertThat(stack.snapshot()).containsExactly(root);
        }

        assertThat(stack.snapshot()).isEmpty();
        assertThat(stack.maxDepth()).isEqualTo(3);
    }

    @Test
    void enter_shouldRejectRecursivePipelineCallsWithReadableCycle() {
        // Given
        PipelineCallStack stack = PipelineCallStack.create();
        PipelineReference root = reference("root");
        PipelineReference child = reference("child");

        // When / Then
        try (PipelineCallStack.Scope ignoredRoot = stack.enter(root);
                PipelineCallStack.Scope ignoredChild = stack.enter(child)) {
            assertThatThrownBy(() -> stack.enter(root))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Pipeline call cycle detected")
                    .hasMessageContaining("root:1 -> child:1 -> root:1");
        }
    }

    @Test
    void enter_shouldRejectDepthAboveConfiguredLimit() {
        // Given
        PipelineCallStack stack = PipelineCallStack.withMaxDepth(1);

        // When / Then
        try (PipelineCallStack.Scope ignored = stack.enter(reference("root"))) {
            assertThatThrownBy(() -> stack.enter(reference("child")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Maximum pipeline call depth exceeded: 1");
        }
    }

    @Test
    void copy_shouldKeepIndependentSnapshotOfCurrentStack() {
        // Given
        PipelineCallStack stack = PipelineCallStack.withMaxDepth(2);
        PipelineReference root = reference("root");

        // When
        PipelineCallStack copy;
        try (PipelineCallStack.Scope ignored = stack.enter(root)) {
            copy = stack.copy();
        }

        // Then
        assertThat(stack.snapshot()).isEmpty();
        assertThat(copy.snapshot()).containsExactly(root);
        assertThat(copy.maxDepth()).isEqualTo(2);
    }

    @Test
    void scopeClose_shouldBeIdempotentAndRejectOutOfOrderClose() {
        // Given
        PipelineCallStack stack = PipelineCallStack.create();
        PipelineCallStack.Scope root = stack.enter(reference("root"));
        PipelineCallStack.Scope child = stack.enter(reference("child"));

        // When / Then
        assertThatThrownBy(root::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid pipeline call stack state");

        child.close();
        root.close();
        root.close();
        assertThat(stack.snapshot()).isEmpty();
    }

    @Test
    void withMaxDepth_shouldRejectNonPositiveDepth() {
        assertThatThrownBy(() -> PipelineCallStack.withMaxDepth(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxDepth must be strictly positive");
    }

    private static PipelineReference reference(String id) {
        return new PipelineReference(id, "1");
    }
}
