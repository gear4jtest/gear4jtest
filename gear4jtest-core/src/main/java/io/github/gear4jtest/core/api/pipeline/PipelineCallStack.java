package io.github.gear4jtest.core.api.pipeline;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the current pipeline-call path to fail fast on recursive pipeline
 * composition.
 */
public final class PipelineCallStack {
    public static final int DEFAULT_MAX_DEPTH = 32;
    private final Deque<PipelineReference> stack;
    private final int maxDepth;

    public PipelineCallStack() {
        this(DEFAULT_MAX_DEPTH);
    }

    public PipelineCallStack(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be strictly positive");
        }
        this.maxDepth = maxDepth;
        this.stack = new ArrayDeque<>();
    }

    private PipelineCallStack(Deque<PipelineReference> stack, int maxDepth) {
        this.stack = new ArrayDeque<>(stack);
        this.maxDepth = maxDepth;
    }

    public Scope enter(PipelineReference reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        if (stack.contains(reference)) {
            throw new IllegalStateException("Pipeline call cycle detected: " + describeCycle(reference));
        }
        if (stack.size() >= maxDepth) {
            throw new IllegalStateException("Maximum pipeline call depth exceeded: " + maxDepth);
        }
        stack.push(reference);
        return new Scope(this, reference);
    }

    public List<PipelineReference> snapshot() {
        return List.copyOf(stack);
    }

    public PipelineCallStack copy() {
        return new PipelineCallStack(stack, maxDepth);
    }

    public int maxDepth() {
        return maxDepth;
    }

    private void leave(PipelineReference expected) {
        PipelineReference current = stack.peek();
        if (!Objects.equals(current, expected)) {
            throw new IllegalStateException(
                    "Invalid pipeline call stack state. Expected " + expected + " but found " + current);
        }
        stack.pop();
    }

    private String describeCycle(PipelineReference reference) {
        StringBuilder builder = new StringBuilder();
        stack.descendingIterator().forEachRemaining(item -> builder.append(item.displayName()).append(" -> "));
        builder.append(reference.displayName());
        return builder.toString();
    }

    public static final class Scope implements AutoCloseable {
        private final PipelineCallStack owner;
        private final PipelineReference reference;
        private boolean closed;

        private Scope(PipelineCallStack owner, PipelineReference reference) {
            this.owner = owner;
            this.reference = reference;
        }

        @Override
        public void close() {
            if (!closed) {
                owner.leave(reference);
                closed = true;
            }
        }
    }
}
