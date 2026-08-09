package io.github.gear4jtest.core.api.assemblyline;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssemblyLineCallStackTest {
    @Test
    void enter_shouldTrackNestedReferencesAndLeaveWhenScopeCloses() {
        // Given
        AssemblyLineCallStack stack = AssemblyLineCallStack.withMaxDepth(3);
        AssemblyLineReference root = reference("root");
        AssemblyLineReference child = reference("child");

        // When / Then
        try (AssemblyLineCallStack.Scope ignoredRoot = stack.enter(root)) {
            assertThat(stack.snapshot()).containsExactly(root);

            try (AssemblyLineCallStack.Scope ignoredChild = stack.enter(child)) {
                assertThat(stack.snapshot()).containsExactly(child, root);
            }

            assertThat(stack.snapshot()).containsExactly(root);
        }

        assertThat(stack.snapshot()).isEmpty();
        assertThat(stack.maxDepth()).isEqualTo(3);
    }

    @Test
    void enter_shouldRejectRecursiveAssemblyLineCallsWithReadableCycle() {
        // Given
        AssemblyLineCallStack stack = AssemblyLineCallStack.create();
        AssemblyLineReference root = reference("root");
        AssemblyLineReference child = reference("child");

        // When / Then
        try (AssemblyLineCallStack.Scope ignoredRoot = stack.enter(root);
                AssemblyLineCallStack.Scope ignoredChild = stack.enter(child)) {
            assertThatThrownBy(() -> stack.enter(root))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AssemblyLine call cycle detected")
                    .hasMessageContaining("root:1 -> child:1 -> root:1");
        }
    }

    @Test
    void enter_shouldRejectDepthAboveConfiguredLimit() {
        // Given
        AssemblyLineCallStack stack = AssemblyLineCallStack.withMaxDepth(1);

        // When / Then
        try (AssemblyLineCallStack.Scope ignored = stack.enter(reference("root"))) {
            assertThatThrownBy(() -> stack.enter(reference("child")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Maximum pipeline call depth exceeded: 1");
        }
    }

    @Test
    void restoreSnapshot_shouldConfineStackToCurrentThreadScope() {
        // Given
        AssemblyLineCallStack stack = AssemblyLineCallStack.create();
        AssemblyLineReference root = reference("root");
        AssemblyLineReference branch = reference("branch");

        // When / Then
        try (AssemblyLineCallStack.Scope ignoredRoot = stack.enter(root)) {
            List<AssemblyLineReference> rootSnapshot = stack.snapshot();
            try (AssemblyLineCallStack.Scope ignoredBranch = stack.enter(branch)) {
                assertThat(stack.snapshot()).containsExactly(branch, root);
                try (AssemblyLineCallStack.Scope ignoredSnapshot = stack.restoreSnapshot(rootSnapshot)) {
                    assertThat(stack.snapshot()).containsExactly(root);
                }
                assertThat(stack.snapshot()).containsExactly(branch, root);
            }
        }
    }

    @Test
    void restoreSnapshot_shouldNotRetainThreadLocalStateOnReusedWorker() throws Exception {
        // Given
        AssemblyLineCallStack stack = AssemblyLineCallStack.create();
        List<AssemblyLineReference> snapshot = List.of(reference("root"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        assertThat(stack.hasCurrentThreadState()).isFalse();

        try {
            // When / Then
            executor.submit(() -> {
                for (int run = 0; run < 10_000; run++) {
                    assertThat(stack.hasCurrentThreadState()).isFalse();
                    try (AssemblyLineCallStack.Scope ignored = stack.restoreSnapshot(snapshot)) {
                        assertThat(stack.hasCurrentThreadState()).isTrue();
                        assertThat(stack.snapshot()).containsExactlyElementsOf(snapshot);
                    }
                    assertThat(stack.hasCurrentThreadState()).isFalse();
                }
            }).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void restoreSnapshot_shouldPreserveExplicitEmptyStateAcrossNestedScopes() {
        // Given
        AssemblyLineCallStack stack = AssemblyLineCallStack.create();

        // When / Then
        try (AssemblyLineCallStack.Scope ignoredOuter = stack.restoreSnapshot(List.of())) {
            assertThat(stack.hasCurrentThreadState()).isTrue();
            try (AssemblyLineCallStack.Scope ignoredInner = stack.restoreSnapshot(List.of())) {
                assertThat(stack.hasCurrentThreadState()).isTrue();
            }
            assertThat(stack.hasCurrentThreadState()).isTrue();
        }
        assertThat(stack.hasCurrentThreadState()).isFalse();
    }

    @Test
    void copy_shouldKeepIndependentSnapshotOfCurrentStack() {
        // Given
        AssemblyLineCallStack stack = AssemblyLineCallStack.withMaxDepth(2);
        AssemblyLineReference root = reference("root");

        // When
        AssemblyLineCallStack copy;
        try (AssemblyLineCallStack.Scope ignored = stack.enter(root)) {
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
        AssemblyLineCallStack stack = AssemblyLineCallStack.create();
        AssemblyLineCallStack.Scope root = stack.enter(reference("root"));
        AssemblyLineCallStack.Scope child = stack.enter(reference("child"));

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
        assertThatThrownBy(() -> AssemblyLineCallStack.withMaxDepth(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxDepth must be strictly positive");
    }

    private static AssemblyLineReference reference(String id) {
        return new AssemblyLineReference(id, "1");
    }
}
