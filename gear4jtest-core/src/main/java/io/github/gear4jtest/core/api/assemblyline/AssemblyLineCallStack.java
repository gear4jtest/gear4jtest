package io.github.gear4jtest.core.api.assemblyline;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the current pipeline-call path to fail fast on recursive pipeline
 * composition.
 *
 * <p>
 * The stack is thread-confined because a single run context may execute several
 * branches concurrently. Runtime infrastructure must propagate the current
 * snapshot when it submits work to another thread.
 * </p>
 */
public final class AssemblyLineCallStack {
    public static final int DEFAULT_MAX_DEPTH = 32;
    private final ThreadLocal<Deque<AssemblyLineReference>> stack = ThreadLocal.withInitial(ArrayDeque::new);
    private final int maxDepth;

    public static AssemblyLineCallStack create() {
        return withMaxDepth(DEFAULT_MAX_DEPTH);
    }

    public static AssemblyLineCallStack withMaxDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be strictly positive");
        }
        return new AssemblyLineCallStack(new ArrayDeque<>(), maxDepth);
    }

    private AssemblyLineCallStack(Deque<AssemblyLineReference> initialStack, int maxDepth) {
        this.stack.set(new ArrayDeque<>(initialStack));
        this.maxDepth = maxDepth;
    }

    public Scope enter(AssemblyLineReference reference) {
        Objects.requireNonNull(reference, "reference must not be null");
        Deque<AssemblyLineReference> currentStack = stack.get();
        if (currentStack.contains(reference)) {
            throw new IllegalStateException(
                    "AssemblyLine call cycle detected: " + describeCycle(currentStack, reference));
        }
        if (currentStack.size() >= maxDepth) {
            throw new IllegalStateException("Maximum pipeline call depth exceeded: " + maxDepth);
        }
        currentStack.push(reference);
        return new Scope(this, reference);
    }

    public Scope restoreSnapshot(List<AssemblyLineReference> snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Deque<AssemblyLineReference> previous = stack.get();
        stack.set(new ArrayDeque<>(snapshot));
        return new Scope(this, previous);
    }

    public List<AssemblyLineReference> snapshot() {
        return List.copyOf(stack.get());
    }

    public AssemblyLineCallStack copy() {
        return new AssemblyLineCallStack(stack.get(), maxDepth);
    }

    public int maxDepth() {
        return maxDepth;
    }

    private void leave(AssemblyLineReference expected) {
        Deque<AssemblyLineReference> currentStack = stack.get();
        AssemblyLineReference current = currentStack.peek();
        if (!Objects.equals(current, expected)) {
            throw new IllegalStateException(
                    "Invalid pipeline call stack state. Expected " + expected + " but found " + current);
        }
        currentStack.pop();
        if (currentStack.isEmpty()) {
            stack.remove();
        }
    }

    private void restore(Deque<AssemblyLineReference> previous) {
        stack.set(previous);
    }

    private static String describeCycle(Deque<AssemblyLineReference> stack, AssemblyLineReference reference) {
        StringBuilder builder = new StringBuilder();
        stack.descendingIterator().forEachRemaining(item -> builder.append(item.displayName()).append(" -> "));
        builder.append(reference.displayName());
        return builder.toString();
    }

    public static final class Scope implements AutoCloseable {
        private final AssemblyLineCallStack owner;
        private final AssemblyLineReference reference;
        private final Deque<AssemblyLineReference> previous;
        private boolean closed;

        private Scope(AssemblyLineCallStack owner, AssemblyLineReference reference) {
            this.owner = owner;
            this.reference = reference;
            this.previous = null;
        }

        private Scope(AssemblyLineCallStack owner, Deque<AssemblyLineReference> previous) {
            this.owner = owner;
            this.reference = null;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                if (previous != null) {
                    owner.restore(previous);
                } else {
                    owner.leave(reference);
                }
                closed = true;
            }
        }
    }
}
