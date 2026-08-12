package io.github.gear4jtest.core.engine.strategy;

import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.execution.trace.StationLogTrace;

/**
 * Explicit internal state of one parallel container branch.
 */
record ParallelBranchOutcome(State state, Optional<StationLogTrace> log) {
    ParallelBranchOutcome {
        state = Objects.requireNonNull(state, "state must not be null");
        log = Objects.requireNonNull(log, "log must not be null");
        if (state.isTerminal() != log.isPresent()) {
            throw new IllegalArgumentException("Branch state " + state
                    + " must " + (state.isTerminal() ? "contain" : "not contain") + " a terminal log");
        }
    }

    static ParallelBranchOutcome notVisited() {
        return new ParallelBranchOutcome(State.NOT_VISITED, Optional.empty());
    }

    static ParallelBranchOutcome submitted() {
        return new ParallelBranchOutcome(State.SUBMITTED, Optional.empty());
    }

    static ParallelBranchOutcome terminal(State state, StationLogTrace log) {
        return new ParallelBranchOutcome(state,
                Optional.of(Objects.requireNonNull(log, "terminal log must not be null")));
    }

    boolean isTerminal() {
        return state.isTerminal();
    }

    StationLogTrace requireLog() {
        return log.orElseThrow(() -> new IllegalStateException("Branch state " + state + " has no terminal log"));
    }

    enum State {
        NOT_VISITED(false),
        SUBMITTED(false),
        COMPLETED(true),
        SKIPPED(true),
        CANCELLED(true),
        REJECTED(true),
        INTERRUPTED(true),
        INVARIANT_FAILURE(true);

        private final boolean terminal;

        State(boolean terminal) {
            this.terminal = terminal;
        }

        boolean isTerminal() {
            return terminal;
        }
    }
}
